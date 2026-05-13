package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.PeriodoTickDTO;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.SimulacionIniciarRequest;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

    private final SimulacionPeriodoService periodoService;
    private final Map<String, SimulacionSesion> sesionesActivas = new ConcurrentHashMap<>();
    private final Map<String, String> wsSessionIdASimSessionId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

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
        final ScheduledFuture<?> tarea = scheduler.scheduleAtFixedRate(
                () -> ejecutarIteracion(sesion, broker),
                sesion.getDuracionDiaSimuladoMs(),
                sesion.getDuracionDiaSimuladoMs(),
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

    private void cancelarTarea(final SimulacionSesion sesion) {
        final ScheduledFuture<?> tarea = sesion.getTareaScheduled();
        if (tarea != null && !tarea.isCancelled()) {
            tarea.cancel(false);
        }
    }
}
