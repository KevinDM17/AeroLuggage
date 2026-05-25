package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.ga.GA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionIniciarRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.PeriodoTickDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulacionSesionManager {

    private static final String TOPIC_TICKS = "/topic/simulacion/";
    private static final String TOPIC_ESTADO = "/topic/simulacion/%s/estado";

    private static final String ESTADO_INICIADA = "INICIADA";
    private static final String ESTADO_PAUSADA = "PAUSADA";
    private static final String ESTADO_REANUDADA = "REANUDADA";
    private static final String ESTADO_DETENIDA = "DETENIDA";
    private static final String ESTADO_FINALIZADA = "FINALIZADA";
    private static final String ESTADO_PLANIFICACION_COMPLETADA = "PLANIFICACION_COMPLETADA";
    private static final long TICK_INTERVAL_MS = 1000L;

    private final SimulacionPeriodoService periodoService;
    private final Map<String, SimulacionSesion> sesionesActivas = new ConcurrentHashMap<>();
    private final Map<String, String> wsSessionIdASimSessionId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ExecutorService planningPool = Executors.newFixedThreadPool(2);

    public SimulacionEstadoDTO iniciar(
            final SimulacionIniciarRequest params,
            final SimpMessagingTemplate broker) {

        final String sessionId = UUID.randomUUID().toString();
        final LocalDate fechaInicio = LocalDate.parse(params.getFechaInicio());
        final SimulacionSesion sesion = new SimulacionSesion(
                sessionId,
                fechaInicio,
                params.getTotalDias(),
                params.getDuracionDiaSimuladoMs()
        );

        sesionesActivas.put(sessionId, sesion);
        programarTarea(sesion, broker);

        log.info("[AeroLuggage/Simulacion] - INICIAR: sessionId: {}", sessionId);

        return SimulacionEstadoDTO.builder()
                .withSessionId(sessionId)
                .withEstado(ESTADO_INICIADA)
                .withMensaje("Simulación iniciada. Suscríbete a /topic/simulacion/" + sessionId)
                .build();
    }

    public void pausar(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.get(sessionId);
        if (sesion == null) {
            log.warn("[AeroLuggage/Simulacion] - PAUSAR: sesión no encontrada: {}", sessionId);
            return;
        }

        cancelarTarea(sesion);
        log.info("[AeroLuggage/Simulacion] - PAUSAR: sessionId: {}", sessionId);

        broker.convertAndSend(
                String.format(TOPIC_ESTADO, sessionId),
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado(ESTADO_PAUSADA)
                        .withMensaje("Simulación pausada en el tick " + sesion.getTickActual().get())
                        .build()
        );
    }

    public void reanudar(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.get(sessionId);
        if (sesion == null) {
            log.warn("[AeroLuggage/Simulacion] - REANUDAR: sesión no encontrada: {}", sessionId);
            return;
        }

        programarTarea(sesion, broker);
        log.info("[AeroLuggage/Simulacion] - REANUDAR: sessionId: {}", sessionId);

        broker.convertAndSend(
                String.format(TOPIC_ESTADO, sessionId),
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado(ESTADO_REANUDADA)
                        .withMensaje("Simulación reanudada desde el tick " + sesion.getTickActual().get())
                        .build()
        );
    }

    public void detener(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.remove(sessionId);
        if (sesion == null) {
            log.warn("[AeroLuggage/Simulacion] - DETENER: sesión no encontrada: {}", sessionId);
            return;
        }

        sesion.getActiva().set(false);
        cancelarTarea(sesion);
        log.info("[AeroLuggage/Simulacion] - DETENER: sessionId: {}", sessionId);

        broker.convertAndSend(
                String.format(TOPIC_ESTADO, sessionId),
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado(ESTADO_DETENIDA)
                        .withMensaje("Simulación detenida manualmente")
                        .build()
        );
    }

    public void registrarWsSession(final String wsSessionId, final String simSessionId) {
        wsSessionIdASimSessionId.put(wsSessionId, simSessionId);
    }

    public SimulacionSesion obtenerSesion(final String sessionId) {
        return sesionesActivas.get(sessionId);
    }

    public void limpiarPorWsSession(final String wsSessionId, final SimpMessagingTemplate broker) {
        final String simSessionId = wsSessionIdASimSessionId.remove(wsSessionId);
        if (simSessionId == null) {
            return;
        }

        final SimulacionSesion sesion = sesionesActivas.remove(simSessionId);
        if (sesion == null) {
            return;
        }

        sesion.getActiva().set(false);
        cancelarTarea(sesion);
        log.info("[AeroLuggage/Simulacion] - DESCONEXION: sesión limpiada: {}", simSessionId);
    }

    private void programarTarea(final SimulacionSesion sesion, final SimpMessagingTemplate broker) {
        final ScheduledFuture<?> tarea = scheduler.scheduleWithFixedDelay(
                () -> ejecutarIteracion(sesion, broker),
                0L,
                TICK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        sesion.setTareaScheduled(tarea);
    }

    private void ejecutarIteracion(final SimulacionSesion sesion, final SimpMessagingTemplate broker) {
        if (!sesion.getActiva().get()) {
            return;
        }

        final PeriodoTickDTO tick = periodoService.ejecutarTick(sesion);
        broker.convertAndSend(TOPIC_TICKS + sesion.getSessionId(), tick);

        planificarAsync(sesion, broker);

        if (sesion.haTerminado()) {
            sesion.getActiva().set(false);
            cancelarTarea(sesion);
            sesionesActivas.remove(sesion.getSessionId());
            log.info("[AeroLuggage/Simulacion] - FINALIZADA: sessionId: {}", sesion.getSessionId());

            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sesion.getSessionId()),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sesion.getSessionId())
                            .withEstado(ESTADO_FINALIZADA)
                            .withMensaje("Simulación completada: " + sesion.getTotalDias() + " días procesados")
                            .build()
            );
        }
    }

    private void planificarAsync(final SimulacionSesion sesion, final SimpMessagingTemplate broker) {
        if (!sesion.hasSnapshotData()) {
            return;
        }
        final SimulacionVentana ventana = sesion.getCurrentWindow().get();
        if (ventana == null) {
            return;
        }
        if (!sesion.necesitaPlanificacion()) {
            return;
        }
        if (!sesion.getPlanificacionEnCurso().compareAndSet(false, true)) {
            return;
        }

        final String windowId = ventana.getWindowId();
        sesion.marcarVentanaPlanificada(windowId);

        planningPool.submit(() -> ejecutarPlanificacion(sesion, windowId, broker));
    }

    private void ejecutarPlanificacion(final SimulacionSesion sesion,
                                       final String windowId,
                                       final SimpMessagingTemplate broker) {
        try {
            final InstanciaProblema instancia = construirInstancia(sesion, windowId);
            if (instancia.getMaletas().isEmpty()) {
                log.info("[AeroLuggage/Planificador] - SKIP: sessionId={}, ventana={}: sin maletas pendientes",
                        sesion.getSessionId(), windowId);
                return;
            }

            log.info("[AeroLuggage/Planificador] - INICIO: sessionId={}, ventana={}, maletas={}",
                    sesion.getSessionId(), windowId, instancia.getMaletas().size());

            final GA ga = new GA();
            ga.ejecutar(instancia);
            final Solucion solucion = ga.getMejorSolucion();

            if (solucion == null || solucion.getSolucion().isEmpty()) {
                log.warn("[AeroLuggage/Planificador] - SIN SOLUCION: sessionId={}, ventana={}",
                        sesion.getSessionId(), windowId);
                return;
            }

            sesion.agregarRutas(solucion.getSolucion());

            log.info("[AeroLuggage/Planificador] - FIN: sessionId={}, ventana={}, rutasNuevas={}, ms={}",
                    sesion.getSessionId(), windowId, solucion.getSolucion().size(), ga.getTiempoEjecucionMs());

            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sesion.getSessionId()),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sesion.getSessionId())
                            .withEstado(ESTADO_PLANIFICACION_COMPLETADA)
                            .withMensaje("Ventana " + windowId + ": " + solucion.getMaletasEntregadasATiempo()
                                    + " maletas planificadas en " + ga.getTiempoEjecucionMs() + "ms")
                            .build()
            );
        } catch (final Exception exception) {
            log.error("[AeroLuggage/Planificador] - ERROR: sessionId={}, ventana={}, error={}",
                    sesion.getSessionId(), windowId, exception.getMessage());
        } finally {
            sesion.getPlanificacionEnCurso().set(false);
        }
    }

    private InstanciaProblema construirInstancia(final SimulacionSesion sesion, final String windowId) {
        final Set<String> maletasConRuta = sesion.getRutas().stream()
                .filter(ruta -> ruta != null && ruta.getEstado() != EstadoRuta.FALLIDA)
                .map(Ruta::getIdMaleta)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final LocalDateTime simTime = sesion.getCurrentSimTimeUtc().get();

        final ArrayList<Maleta> pendientes = sesion.getMaletas().stream()
                .filter(maleta -> maleta != null
                        && maleta.getIdMaleta() != null
                        && maleta.getFechaRegistro() != null
                        && !maleta.getFechaRegistro().isAfter(simTime)
                        && !maletasConRuta.contains(maleta.getIdMaleta()))
                .collect(Collectors.toCollection(ArrayList::new));

        return new InstanciaProblema(
                "SIM-" + sesion.getSessionId() + "-" + windowId,
                pendientes,
                new ArrayList<>(sesion.getVuelosProgramados()),
                new ArrayList<>(sesion.getVuelosInstancia()),
                new ArrayList<>(sesion.getAeropuertos())
        );
    }

    private void cancelarTarea(final SimulacionSesion sesion) {
        final ScheduledFuture<?> tarea = sesion.getTareaScheduled();
        if (tarea != null && !tarea.isCancelled()) {
            tarea.cancel(false);
        }
    }
}
