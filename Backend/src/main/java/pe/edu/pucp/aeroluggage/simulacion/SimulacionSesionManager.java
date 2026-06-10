package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ALNSUtil;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ALNS;
import pe.edu.pucp.aeroluggage.config.ALNSConfig;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.config.SimulacionParams;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaVueloResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloInstanciaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionIniciarRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionTickLigeroDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulacionSesionManager {

    private static final String TOPIC_TICKS = "/topic/simulacion/";
    private static final String TOPIC_ESTADO = "/topic/simulacion/%s/estado";
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String ESTADO_INICIADA = "INICIADA";
    private static final String ESTADO_PAUSADA = "PAUSADA";
    private static final String ESTADO_REANUDADA = "REANUDADA";
    private static final String ESTADO_DETENIDA = "DETENIDA";
    private static final String ESTADO_FINALIZADA = "FINALIZADA";
    private static final String ESTADO_PLANIFICACION_COMPLETADA = "PLANIFICACION_COMPLETADA";

    private final SimulacionPeriodoService periodoService;
    private final SimulacionBootstrapService bootstrapService;
    private final SimulacionSnapshotService snapshotService;
    private final SimulacionParams simulacionParams;
    private final pe.edu.pucp.aeroluggage.config.SistemaConfiguracion sistemaConfiguracion;
    private final ALNSConfig alnsConfig;
    private final Map<String, SimulacionSesion> sesionesActivas = new ConcurrentHashMap<>();
    private final Map<String, SimulacionSesion> sesionesFinalizadas = new ConcurrentHashMap<>();
    private final Map<String, String> wsSessionIdASimSessionId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ExecutorService planningPool = Executors.newFixedThreadPool(2);

    public SimulacionEstadoDTO iniciar(
            final SimulacionIniciarRequest params,
            final SimpMessagingTemplate broker) {

        final String sessionId = UUID.randomUUID().toString();
        final LocalDate fechaInicio = LocalDate.parse(params.getFechaInicio());
        final LocalTime horaInicio = params.getHoraInicio() != null && !params.getHoraInicio().isBlank()
                ? LocalTime.parse(params.getHoraInicio())
                : LocalTime.MIDNIGHT;
        final SimulacionSesion sesion = new SimulacionSesion(
                sessionId,
                fechaInicio,
                horaInicio,
                params.getTotalDias(),
                simulacionParams.getDuracionDiaSimuladoMs(),
                simulacionParams.getVentana().getTamanioMinutos(),
                simulacionParams.getVentana().getEspaciadoMinutos(),
                params.getHusoGMT()
        );

        sesionesActivas.put(sessionId, sesion);
        sesion.setUmbralConfirmacionMinutos(sistemaConfiguracion.getUmbralConfirmacionMinutos());

        bootstrapService.ensureSnapshotLoaded(sesion);

        final String primeraVentana = sesion.getCurrentWindow().get().getWindowId();
        final long bucketInicial = SimulacionSesion.parseBucket(primeraVentana);
        sesion.asegurarVuelosParaBanda(bucketInicial, bucketInicial + 24L);

        sesion.construirIndiceEventos(sistemaConfiguracion.getUmbralConfirmacionMinutos());
        snapshotService.recalcularEstadoSesion(sesion);

        planificarInicial(sesion, primeraVentana, broker, bucketInicial);

        log.info("[AeroLuggage/Simulacion] - INICIAR: sessionId: {}, ventanaInicial: {}", sessionId, primeraVentana);

        return SimulacionEstadoDTO.builder()
                .withSessionId(sessionId)
                .withEstado(ESTADO_INICIADA)
                .withMensaje("Simulaci\u00f3n iniciada. Suscr\u00edbete a /topic/simulacion/" + sessionId)
                .build();
    }

    public void pausar(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.get(sessionId);
        if (sesion == null) {
            return;
        }

        cancelarTarea(sesion);

        broker.convertAndSend(
                String.format(TOPIC_ESTADO, sessionId),
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado(ESTADO_PAUSADA)
                        .withMensaje("Simulaci\u00f3n pausada en el tick " + sesion.getTickActual().get())
                        .build()
        );
    }

    public void reanudar(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.get(sessionId);
        if (sesion == null) {
            return;
        }

        programarTarea(sesion, broker);

        broker.convertAndSend(
                String.format(TOPIC_ESTADO, sessionId),
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado(ESTADO_REANUDADA)
                        .withMensaje("Simulaci\u00f3n reanudada desde el tick " + sesion.getTickActual().get())
                        .build()
        );
    }

    public void detener(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.remove(sessionId);
        sesionesFinalizadas.remove(sessionId);
        if (sesion == null) {
            log.warn("[AeroLuggage/Simulacion] - DETENER: sesi\u00f3n no encontrada: {}", sessionId);
            return;
        }

        sesion.getActiva().set(false);
        limpiarSesion(sesion);
        wsSessionIdASimSessionId.values().remove(sessionId);
        log.info("[AeroLuggage/Simulacion] - DETENER: sessionId: {}", sessionId);

        broker.convertAndSend(
                String.format(TOPIC_ESTADO, sessionId),
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado(ESTADO_DETENIDA)
                        .withMensaje("Simulaci\u00f3n detenida manualmente")
                        .build()
        );
    }

    public void registrarWsSession(final String wsSessionId, final String simSessionId) {
        wsSessionIdASimSessionId.put(wsSessionId, simSessionId);
    }

    public SimulacionSesion obtenerSesion(final String sessionId) {
        return sesionesActivas.get(sessionId);
    }

    public SimulacionSesion obtenerSesionFinalizada(final String sessionId) {
        return sesionesFinalizadas.get(sessionId);
    }

    public void limpiarPorWsSession(final String wsSessionId, final SimpMessagingTemplate broker) {
        final String simSessionId = wsSessionIdASimSessionId.remove(wsSessionId);
        if (simSessionId == null) {
            return;
        }

        final SimulacionSesion sesion = sesionesActivas.remove(simSessionId);
        sesionesFinalizadas.remove(simSessionId);
        if (sesion == null) {
            return;
        }

        sesion.getActiva().set(false);
        limpiarSesion(sesion);
        log.info("[AeroLuggage/Simulacion] - DESCONEXION: sesi\u00f3n limpiada: {}", simSessionId);
    }

    private void programarTarea(final SimulacionSesion sesion, final SimpMessagingTemplate broker) {
        final long tickIntervalMs = simulacionParams.getTickIntervalMs();
        final ScheduledFuture<?> tarea = scheduler.scheduleWithFixedDelay(
                () -> ejecutarIteracion(sesion, broker),
                tickIntervalMs,
                tickIntervalMs,
                TimeUnit.MILLISECONDS
        );
        sesion.setTareaScheduled(tarea);
    }

    private void ejecutarIteracion(final SimulacionSesion sesion, final SimpMessagingTemplate broker) {
        if (!sesion.getActiva().get()) {
            return;
        }

        final SimulacionTickLigeroDTO tick = periodoService.ejecutarTick(sesion);
        broker.convertAndSend(TOPIC_TICKS + sesion.getSessionId(), tick);

        planificarAsync(sesion, broker);

        if (sesion.haTerminado()) {
            sesion.getActiva().set(false);
            limpiarSesion(sesion);
            sesionesActivas.remove(sesion.getSessionId());
            sesionesFinalizadas.remove(sesion.getSessionId());
            log.info("[AeroLuggage/Simulacion] - FINALIZADA: sessionId: {}", sesion.getSessionId());

            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sesion.getSessionId()),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sesion.getSessionId())
                            .withEstado(ESTADO_FINALIZADA)
                            .withMensaje("Simulaci\u00f3n completada: " + sesion.getTotalDias() + " d\u00edas procesados")
                            .build()
            );
        }
    }

    private void planificarSync(final SimulacionSesion sesion,
                                 final String windowId,
                                 final SimpMessagingTemplate broker) {
        sesion.iniciarPlanificacion();
        sesion.marcarVentanaPlanificada(windowId);
        ejecutarPlanificacion(sesion, windowId, broker);
    }

    private void planificarAsync(final SimulacionSesion sesion, final SimpMessagingTemplate broker) {
        if (!sesion.hasSnapshotData()) {
            return;
        }
        final SimulacionVentana ventana = sesion.getCurrentWindow().get();
        if (ventana == null) {
            return;
        }
        if (sesion.hayReplanPendiente()) {
            return;
        }
        if (!sesion.necesitaPlanificacion()) {
            return;
        }
        if (!sesion.iniciarPlanificacion()) {
            return;
        }

        final long bucketActual = SimulacionSesion.parseBucket(ventana.getWindowId());
        final String siguienteVentana = "W" + String.format("%04d", bucketActual + 1L);
        sesion.marcarVentanaPlanificada(siguienteVentana);

        planningPool.submit(() -> ejecutarPlanificacion(sesion, siguienteVentana, broker));
    }

    private void planificarInicial(final SimulacionSesion sesion,
                                   final String primeraVentana,
                                   final SimpMessagingTemplate broker,
                                   final long bucketInicial) {
        final String segundaVentana = "W" + String.format("%04d", bucketInicial + 1L);
        sesion.iniciarPlanificacion();
        sesion.marcarVentanaPlanificada(primeraVentana);
        sesion.marcarVentanaPlanificada(segundaVentana);
        planningPool.submit(() -> {
            ejecutarPlanificacion(sesion, primeraVentana, broker);
            ejecutarPlanificacion(sesion, segundaVentana, broker);
        });
    }

    public void iniciarTicks(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.get(sessionId);
        if (sesion == null || sesion.getTareaScheduled() != null) {
            return;
        }
        programarTarea(sesion, broker);
    }

    public void reconciliarEstado(final String sessionId, final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.get(sessionId);
        if (sesion == null) {
            return;
        }
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        final SimulacionSesion.TickSnapshot snap = sesion.consolidar(simTimeUtc);
        final SimulacionTickLigeroDTO dto = SimulacionTickLigeroDTO.builder()
                .withType("RECONCILIAR")
                .withTick(sesion.getTickActual().get())
                .withSimTime(simTimeUtc.format(FORMATO_FECHA_HORA))
                .withStateVersion(sesion.getStateVersion().get())
                .withMaletasEnTransito(snap.enTransito())
                .withMaletasEntregadas(snap.entregadas())
                .withMaletasRetrasadas(0)
                .withMaletasNoAsignadas(snap.sinRuta())
                .withVuelosActivos(snap.vuelosActivos())
                .withCapacidadLibrePct(snap.capacidadLibrePct())
                .withEstadosMaletas(snap.estadosMaletas())
                .withEstadosRutas(snap.estadosRutas())
                .withEstadosVuelos(snap.estadosVuelos())
                .withAeropuertos(snap.aeropuertos())
                .build();
        broker.convertAndSend(TOPIC_TICKS + sesion.getSessionId(), dto);
    }

    private void ejecutarPlanificacion(final SimulacionSesion sesion,
                                       final String windowId,
                                       final SimpMessagingTemplate broker) {
        if (!sesion.getActiva().get()) {
            return;
        }
        ALNS alns = null;
        long tiempoPlanMs = 0L;
        try {
            final long inicioPlan = System.currentTimeMillis();
            final InstanciaProblema instancia = construirInstancia(sesion, windowId);
            final var ventana = sesion.getCurrentWindow().get();
            final LocalDateTime inicioVentana = calcularInicioVentana(sesion, windowId);
            final LocalDateTime finVentana = inicioVentana.plusMinutes(sesion.getWindowSizeMinutes());
            final int enrutadasPrevias = totalEnrutadas(sesion.getResumenesVentana());
            final int ocupMaxAero = maxOcupacionAeropuertoPct(sesion.getAeropuertos());
            final int ocupMaxVuelo = maxOcupacionVueloPct(instancia.getVueloInstancias());

            if (instancia.getMaletas().isEmpty()) {
                log.info("[AeroLuggage/Planificador] - SKIP: sessionId={}\n"
                                + "\tventana={}\n"
                                + "\tinicioVentana={}\n"
                                + "\tfinVentana={}\n"
                                + "\tsimTime={}\n"
                                + "\tmaletasEvaluadas=0\n"
                                + "\tmensaje=sin maletas pendientes",
                        sesion.getSessionId(),
                        windowId, inicioVentana, finVentana,
                        sesion.getCurrentSimTimeUtc().get());
                if (ventana != null) {
                    sesion.registrarResumenVentana(new SimulacionSesion.ResumenVentanaPlanificacion(
                            windowId,
                            ventana.getStartUtc(),
                            ventana.getEndUtc(),
                            0,
                            0,
                            0,
                            0L
                    ));
                }
                return;
            }

            alns = new ALNS(alnsConfig.toParametrosALNS());
            final InstanciaProblema copia = instancia.deepCopy();
            alns.ejecutar(copia);
            tiempoPlanMs = System.currentTimeMillis() - inicioPlan;
            final Solucion solucion = alns.getMejorSolucion();

            registrarFallosMaletas(sesion, copia, windowId, alns);

            if (solucion == null || solucion.getSolucion().isEmpty()) {
                final int iteraciones = alns != null ? alns.getIteracionesEjecutadas() : 0;
                log.warn("[AeroLuggage/Planificador] - SIN SOLUCION: sessionId={}\n"
                                + "\tventana={}\n"
                                + "\tmaletasEvaluadas={}\n"
                                + "\tmaletasEnrutadas=0\n"
                                + "\tmaletasSinRuta={}\n"
                                + "\ttotalEnrutadasSimulacion={}\n"
                                + "\ttiempoPlanificadorMs={}\n"
                                + "\titeraciones={}\n"
                                + "\tocupacionMaxAeropuerto={}%\n"
                                + "\tocupacionMaxVuelo={}%",
                        sesion.getSessionId(),
                        windowId,
                        instancia.getMaletas().size(),
                        instancia.getMaletas().size(),
                        enrutadasPrevias,
                        tiempoPlanMs,
                        iteraciones,
                        ocupMaxAero, ocupMaxVuelo);
                if (ventana != null) {
                    sesion.registrarResumenVentana(new SimulacionSesion.ResumenVentanaPlanificacion(
                            windowId,
                            ventana.getStartUtc(),
                            ventana.getEndUtc(),
                            instancia.getMaletas().size(),
                            0,
                            instancia.getMaletas().size(),
                            tiempoPlanMs
                    ));
                }
                return;
            }

            final List<Ruta> nuevasRutas = solucion.getSolucion().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            sesion.agregarRutas(nuevasRutas);
            for (final Ruta nr : nuevasRutas) {
                sesion.onRutaAgregada(nr, sistemaConfiguracion.getUmbralConfirmacionMinutos());
            }
            if (ventana != null) {
                final long enrutadasValidas = solucion.getSolucion().stream()
                        .filter(Objects::nonNull)
                        .count();
                final int enrutadas = (int) enrutadasValidas;
                final int evaluadas = instancia.getMaletas().size();
                final int sinRuta = Math.max(0, evaluadas - enrutadas);
                final int totalEnrutadasSim = enrutadasPrevias + enrutadas;

                final int iteraciones = alns != null ? alns.getIteracionesEjecutadas() : 0;
                log.info("[AeroLuggage/Planificador] - PLANIFICACIÓN: sessionId={}\n"
                                + "\tventana={}\n"
                                + "\tmaletasEvaluadas={}\n"
                                + "\tmaletasEnrutadas={}\n"
                                + "\tmaletasSinRuta={}\n"
                                + "\ttotalEnrutadasSimulacion={}\n"
                                + "\ttiempoPlanificadorMs={}\n"
                                + "\titeraciones={}\n"
                                + "\tocupacionMaxAeropuerto={}%\n"
                                + "\tocupacionMaxVuelo={}%",
                        sesion.getSessionId(),
                        windowId,
                        evaluadas, enrutadas, sinRuta,
                        totalEnrutadasSim,
                        tiempoPlanMs,
                        iteraciones,
                        ocupMaxAero, ocupMaxVuelo);
                sesion.registrarResumenVentana(new SimulacionSesion.ResumenVentanaPlanificacion(
                        windowId,
                        ventana.getStartUtc(),
                        ventana.getEndUtc(),
                        evaluadas,
                        enrutadas,
                        sinRuta,
                        tiempoPlanMs
                ));

                if (sinRuta > 0) {
                    final Map<String, Long> razones = alns.getUltimasRazonesFallo().entrySet().stream()
                            .collect(Collectors.groupingBy(
                                    Map.Entry::getValue,
                                    Collectors.counting()
                            ));
                    if (!razones.isEmpty()) {
                        log.info("[AeroLuggage/Planificador] - RAZONES SIN RUTA: ventana={}", windowId);
                        for (final Map.Entry<String, Long> entry : razones.entrySet()) {
                            log.info("\t{}: {}", entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

            final long bucketPlan = SimulacionSesion.parseBucket(windowId);
            final long horizontePlan = bucketPlan + 24L;
            final long enviadoPlan = sesion.getUltimoIndiceVuelosEnviado().get();
            String vuelosDesde = null;
            String vuelosHasta = null;
            if (horizontePlan > enviadoPlan) {
                vuelosDesde = "W" + String.format("%04d", Math.max(enviadoPlan + 1L, 1L));
                vuelosHasta = "W" + String.format("%04d", horizontePlan);
                sesion.getUltimoIndiceVuelosEnviado().set(horizontePlan);
            }
            java.util.Map<String, Object> readyMsg = new java.util.HashMap<>();
            readyMsg.put("type", "VENTANA_READY");
            readyMsg.put("ventana", windowId);
            if (vuelosDesde != null) {
                readyMsg.put("vuelosDesde", vuelosDesde);
                readyMsg.put("vuelosHasta", vuelosHasta);
            }
            log.info("[AeroLuggage/Simulacion] - VENTANA_READY: sessionId={}, ventana={}, vuelosDesde={}, vuelosHasta={}, "
                    + "totalVuelosInstancia={}, maletasCalientes={}, rutas={}, aeropuertos={}, "
                    + "vuelosFiltrados={}",
                    sesion.getSessionId(), windowId, vuelosDesde, vuelosHasta,
                    sesion.getVuelosInstancia().size(),
                    sesion.getMaletasCalientes().size(),
                    sesion.getRutas().size(),
                    sesion.getAeropuertos().size(),
                    instancia.getVueloInstancias().size());
            broker.convertAndSend(
                    (String) (TOPIC_TICKS + sesion.getSessionId()),
                    (Object) readyMsg
            );

            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sesion.getSessionId()),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sesion.getSessionId())
                            .withEstado(ESTADO_PLANIFICACION_COMPLETADA)
                            .withMensaje("Ventana " + windowId + ": " + solucion.getMaletasEntregadasATiempo()
                                    + " maletas planificadas en " + tiempoPlanMs + "ms")
                            .build()
            );
        } catch (final Exception exception) {
            log.error("[AeroLuggage/Planificador] - ERROR: sessionId={}, ventana={}, error={}",
                    sesion.getSessionId(), windowId, exception.getMessage());
        } finally {
            if (alns != null) {
                alns.limpiarInstancia();
            }
            sesion.finalizarPlanificacion();
            sesion.marcarPlanValido();
            if (sesion.hayReplanPendiente()) {
                sesion.limpiarReplanPendiente();
                dispararReplan(sesion, broker);
            }
            if (sesion.haTerminado() && sesion.marcarCsvEscrito()) {
                escribirCsvFallos(sesion);
            }
        }
    }

    private static void registrarFallosMaletas(final SimulacionSesion sesion,
                                               final InstanciaProblema instancia,
                                               final String windowId,
                                               final ALNS alns) {
        final Map<String, String> razones = alns.getUltimasRazonesFallo();
        if (razones == null || razones.isEmpty()) {
            return;
        }
        log.info("[AeroLuggage/Simulacion] - REGISTRANDO FALLOS: windowId={}, totalMaletas={}",
                windowId, razones.size());
        for (final Map.Entry<String, String> entry : razones.entrySet()) {
            final String idMaleta = entry.getKey();
            final String motivo = entry.getValue();
            if (idMaleta == null || motivo == null) {
                continue;
            }
            final Maleta maleta = instancia.getMaletas().stream()
                    .filter(m -> m != null && idMaleta.equals(m.getIdMaleta()))
                    .findFirst().orElse(null);
            if (maleta == null) {
                continue;
            }
            final String codigoOrigen = maleta.getPedido() != null
                    && maleta.getPedido().getAeropuertoOrigen() != null
                    ? maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto()
                    : "";
            final String fechaRegistro = maleta.getFechaRegistro() != null
                    ? maleta.getFechaRegistro().toString()
                    : "";
            sesion.registrarFalloEnVentana(idMaleta, codigoOrigen, fechaRegistro, windowId, motivo);
        }
    }

    private static void escribirCsvFallos(final SimulacionSesion sesion) {
        final List<String> lineas = sesion.getNoEnrutadasParaCsv();
        log.info("[AeroLuggage/Simulacion] - ESCRIBIENDO CSV: lineas={}", lineas.size());
        if (lineas.size() <= 1) {
            log.info("[AeroLuggage/Simulacion] - CSV no generado: sin maletas sin ruta");
            return;
        }
        final Path ruta = Path.of("documentos", "Resultados", "maletas_no_enrutadas_detalle.csv");
        log.info("[AeroLuggage/Simulacion] - RUTA CSV: {}", ruta.toAbsolutePath());
        try {
            Files.createDirectories(ruta.getParent());
            Files.write(ruta, lineas, StandardCharsets.UTF_8);
            log.info("[AeroLuggage/Simulacion] - CSV generado: {} ({} lineas)",
                    ruta.toAbsolutePath(), lineas.size());
        } catch (final IOException exception) {
            log.error("[AeroLuggage/Simulacion] - Error al escribir CSV: {}", exception.getMessage());
        }
    }

    public void cancelarVuelo(final String sessionId, final String idVuelo,
                               final List<String> idsMaletasFrontend,
                               final SimpMessagingTemplate broker) {
        final SimulacionSesion sesion = sesionesActivas.get(sessionId);
        if (sesion == null) {
            return;
        }
        if (idVuelo != null) {
            final VueloInstancia vuelo = buscarVuelo(sesion, idVuelo);
            if (vuelo == null) {
                return;
            }
            if (vuelo.getEstado() == EstadoVuelo.EN_PROGRESO
                    || vuelo.getEstado() == EstadoVuelo.FINALIZADO
                    || vuelo.getEstado() == EstadoVuelo.CONFIRMADO
                    || vuelo.getEstado() == EstadoVuelo.CANCELADO) {
                log.warn("[Cancelacion] Vuelo {} no cancelable: estado={}", idVuelo, vuelo.getEstado());
                return;
            }
            vuelo.cancelar();
            sesion.moverVueloAFrio(idVuelo);
        }

        final LocalDateTime simTime = sesion.getCurrentSimTimeUtc().get();
        int totalRutas = 0;
        int totalManietas = 0;

        final Set<String> maletasYaProcesadas = new HashSet<>();

        if (idVuelo != null) {
            for (final Ruta ruta : sesion.getRutas()) {
                if (ruta == null || ruta.getEstado() == EstadoRuta.COMPLETADA) {
                    continue;
                }
                if (!contieneVuelo(ruta, idVuelo)) {
                    continue;
                }
                totalRutas++;
                final Maleta maleta = sesion.getMaletasPorId().get(ruta.getIdMaleta());
                if (maleta == null) {
                    continue;
                }
                final String origenActual = determinarUbicacionActual(ruta, idVuelo, simTime,
                        maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto(),
                        sesion.getVueloIndex());
                if (origenActual == null) {
                    continue;
                }
                maletasYaProcesadas.add(maleta.getIdMaleta());
                totalManietas++;
                final Pedido pedido = maleta.getPedido();
                sesion.agregarSegmentoReplanificacion(new SimulacionSesion.SegmentoReplanificacion(
                        maleta.getIdMaleta(),
                        origenActual,
                        pedido.getAeropuertoDestino().getIdAeropuerto(),
                        simTime,
                        pedido.getFechaHoraPlazo()
                ));
                sesion.onRutaCancelada(ruta);
            }
        }

        if (idsMaletasFrontend != null && !idsMaletasFrontend.isEmpty()) {
            for (final String idMaleta : idsMaletasFrontend) {
                if (idMaleta == null || maletasYaProcesadas.contains(idMaleta)) {
                    continue;
                }
                final Ruta ruta = sesion.getRutasPorMaleta().get(idMaleta);
                if (ruta == null || ruta.getEstado() == EstadoRuta.COMPLETADA) {
                    continue;
                }
                final Maleta maleta = sesion.getMaletasPorId().get(idMaleta);
                if (maleta == null || maleta.getPedido() == null) {
                    continue;
                }
                final String origenActual = determinarUbicacionActual(
                        ruta, null, simTime,
                        maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto(),
                        sesion.getVueloIndex());
                if (origenActual == null) {
                    continue;
                }
                maletasYaProcesadas.add(idMaleta);
                totalManietas++;
                final Pedido pedido = maleta.getPedido();
                sesion.agregarSegmentoReplanificacion(new SimulacionSesion.SegmentoReplanificacion(
                        idMaleta,
                        origenActual,
                        pedido.getAeropuertoDestino().getIdAeropuerto(),
                        simTime,
                        pedido.getFechaHoraPlazo()
                ));
                sesion.onRutaCancelada(ruta);
            }
        }

        if (maletasYaProcesadas.isEmpty()) {
            log.info("[Cancelacion] - SIN MALETAS AFECTADAS: sessionId={}, vuelo={}, maletasFrontend={}",
                    sessionId, idVuelo, idsMaletasFrontend);
            return;
        }

        sesion.marcarPlanInvalido();
        final boolean replanYaSolicitado = sesion.hayReplanPendiente();
        final boolean planEnCurso = sesion.estaPlanificando();
        if (!replanYaSolicitado) {
            sesion.solicitarReplan();
        }
        log.info("[Cancelacion] - RESULTADO: sessionId={}, vuelo={}, rutasAfectadas={}, "
                + "maletasReplanificadas={}, planEnCurso={}, replanSolicitado={}",
                sessionId, idVuelo, totalRutas, totalManietas, planEnCurso, !replanYaSolicitado);
        if (!planEnCurso) {
            dispararReplan(sesion, broker);
        }
    }

    private static VueloInstancia buscarVuelo(final SimulacionSesion sesion, final String idVuelo) {
        return sesion.getVuelosInstancia().stream()
                .filter(v -> v != null && idVuelo.equals(v.getIdVueloInstancia()))
                .findFirst().orElse(null);
    }

    private static boolean contieneVuelo(final Ruta ruta, final String idVuelo) {
        final List<String> ids = ruta.getSubrutaIds();
        for (final String id : ids) {
            if (idVuelo.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String determinarUbicacionActual(final Ruta ruta, final String idVueloCancelado,
                                                    final LocalDateTime simTime, final String origenIcao,
                                                    final Map<String, VueloInstancia> vueloIndex) {
        final List<String> ids = ruta.getSubrutaIds();
        if (ids.isEmpty()) {
            return null;
        }
        if (idVueloCancelado != null) {
            for (int i = 0; i < ids.size(); i++) {
                if (!idVueloCancelado.equals(ids.get(i))) {
                    continue;
                }
                if (i == 0) {
                    return origenIcao;
                }
                for (int j = i - 1; j >= 0; j--) {
                    final VueloInstancia anterior = vueloIndex.get(ids.get(j));
                    if (anterior != null && anterior.getFechaLlegada() != null
                            && !anterior.getFechaLlegada().isAfter(simTime)) {
                        return anterior.getAeropuertoDestino().getIdAeropuerto();
                    }
                }
                return origenIcao;
            }
            return null;
        }
        String ultimaUbicacion = origenIcao;
        for (final String id : ids) {
            final VueloInstancia v = vueloIndex.get(id);
            if (v == null) {
                continue;
            }
            if (v.getFechaLlegada() != null && !v.getFechaLlegada().isAfter(simTime)) {
                ultimaUbicacion = v.getAeropuertoDestino().getIdAeropuerto();
            }
        }
        return ultimaUbicacion;
    }

    private void dispararReplan(final SimulacionSesion sesion,
                                final SimpMessagingTemplate broker) {
        if (!sesion.iniciarPlanificacion()) {
            return;
        }
        final SimulacionVentana ventana = sesion.getCurrentWindow().get();
        final String windowId = ventana != null ? ventana.getWindowId() : "W0001";
        planningPool.submit(() -> {
            try {
                ejecutarPlanificacion(sesion, windowId, broker);
            } finally {
                sesion.finalizarPlanificacion();
                if (sesion.hayReplanPendiente()) {
                    sesion.limpiarReplanPendiente();
                    dispararReplan(sesion, broker);
                }
            }
        });
    }

    private static LocalDateTime calcularInicioVentana(final SimulacionSesion sesion, final String windowId) {
        final long bucket = Long.parseLong(windowId.substring(1)) - 1L;
        return sesion.getFechaInicioUtc().plusMinutes(bucket * sesion.getWindowSpacingMinutes());
    }

    private static int totalEnrutadas(final List<SimulacionSesion.ResumenVentanaPlanificacion> resumenes) {
        int total = 0;
        for (final var resumen : resumenes) {
            total += resumen.maletasEnrutadas();
        }
        return total;
    }

    private static int maxOcupacionAeropuertoPct(final List<Aeropuerto> aeropuertos) {
        int maxPct = 0;
        for (final Aeropuerto a : aeropuertos) {
            if (a == null || a.getCapacidadAlmacen() <= 0) {
                continue;
            }
            final int pct = Math.min(100, a.getMaletasActuales() * 100 / a.getCapacidadAlmacen());
            if (pct > maxPct) {
                maxPct = pct;
            }
        }
        return maxPct;
    }

    private static int maxOcupacionVueloPct(final List<VueloInstancia> vuelos) {
        int maxPct = 0;
        for (final VueloInstancia v : vuelos) {
            if (v == null || v.getCapacidadMaxima() <= 0) {
                continue;
            }
            final int usadas = v.getCapacidadMaxima() - Math.max(0, v.getCapacidadDisponible());
            final int pct = Math.min(100, usadas * 100 / v.getCapacidadMaxima());
            if (pct > maxPct) {
                maxPct = pct;
            }
        }
        return maxPct;
    }

    private InstanciaProblema construirInstancia(final SimulacionSesion sesion, final String windowId) {
        final long bucketActual = SimulacionSesion.parseBucket(windowId);
        sesion.asegurarVuelosParaBanda(bucketActual, bucketActual + 24L);

        final LocalDateTime inicioVentana = calcularInicioVentana(sesion, windowId);
        final LocalDateTime finVentana = inicioVentana.plusMinutes(sesion.getWindowSizeMinutes());
        sesion.asegurarPedidosParaVentana(finVentana);

        final ArrayList<VueloInstancia> vuelosInstanciaCopia = sesion.getVuelosInstancia().stream()
                .map(v -> new VueloInstancia(
                        v.getIdVueloInstancia(),
                        v.getCodigo(),
                        v.getVueloProgramado(),
                        v.getFechaOperacion(),
                        v.getFechaSalida(),
                        v.getFechaLlegada(),
                        v.getCapacidadMaxima(),
                        v.getCapacidadDisponible(),
                        v.getAeropuertoOrigen(),
                        v.getAeropuertoDestino(),
                        v.getEstado()
                ))
                .collect(Collectors.toCollection(ArrayList::new));

        final Map<String, VueloInstancia> idxVuelos = vuelosInstanciaCopia.stream()
                .collect(Collectors.toMap(VueloInstancia::getIdVueloInstancia, v -> v, (a, b) -> a));

        final ArrayList<Ruta> rutasComprometidas = sesion.getRutas().stream()
                .filter(ruta -> ruta != null
                        && ruta.getEstado() != EstadoRuta.REPLANIFICADA)
                .map(ruta -> new Ruta(
                        ruta.getIdRuta(), ruta.getIdMaleta(),
                        ruta.getPlazoMaximoDias(), ruta.getDuracion(),
                        ruta.getSubrutaIds(), ruta.getEstado(), ruta.getFechaEntrega()))
                .collect(Collectors.toCollection(ArrayList::new));

        final Set<String> maletasConRuta = rutasComprometidas.stream()
                .map(Ruta::getIdMaleta)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final ArrayList<Maleta> pendientes = sesion.getMaletasCalientes().stream()
                .filter(maleta -> maleta != null
                        && maleta.getIdMaleta() != null
                        && maleta.getFechaRegistro() != null
                        && !maleta.getFechaRegistro().isAfter(finVentana)
                        && !maletasConRuta.contains(maleta.getIdMaleta()))
                .collect(Collectors.toCollection(ArrayList::new));

        final List<SimulacionSesion.SegmentoReplanificacion> segmentos =
                sesion.obtenerSegmentosReplanificacion();
        if (!segmentos.isEmpty()) {
            final Set<String> idsConSegmento = segmentos.stream()
                    .map(SimulacionSesion.SegmentoReplanificacion::idMaleta)
                    .collect(Collectors.toSet());
            pendientes.removeIf(m -> idsConSegmento.contains(m.getIdMaleta()));

            final Map<String, Aeropuerto> indiceAeropuertos = sesion.getAeropuertos().stream()
                    .filter(a -> a != null && a.getIdAeropuerto() != null)
                    .collect(Collectors.toMap(Aeropuerto::getIdAeropuerto, a -> a, (a, b) -> a));

            for (final SimulacionSesion.SegmentoReplanificacion s : segmentos) {
                final Aeropuerto origenActual = indiceAeropuertos.get(s.origenActual());
                final Aeropuerto destino = indiceAeropuertos.get(s.destinoOriginal());
                if (origenActual == null || destino == null) {
                    continue;
                }
                final Pedido pedidoVirtual = new Pedido(
                        s.idMaleta(),
                        origenActual,
                        destino,
                        s.tDisponible(),
                        s.tLimite(),
                        1,
                        EstadoPedido.REGISTRADO
                );
                final Maleta maletaVirtual = new Maleta(
                        s.idMaleta(),
                        pedidoVirtual,
                        s.tDisponible(),
                        null,
                        EstadoMaleta.EN_ALMACEN.name()
                );
                pendientes.add(maletaVirtual);
            }
        }

        final Map<String, Ruta> idxRutas = new HashMap<>();
        for (final Ruta r : rutasComprometidas) {
            if (r != null && r.getIdRuta() != null) idxRutas.put(r.getIdRuta(), r);
        }

        final ArrayList<Aeropuerto> aeropuertosCopia = sesion.getAeropuertos().stream()
                .map(a -> new Aeropuerto(
                        a.getIdAeropuerto(),
                        a.getCiudad(),
                        a.getCapacidadAlmacen(),
                        a.getMaletasActuales(),
                        a.getLongitud(),
                        a.getLatitud(),
                        a.getHusoGMT()))
                .collect(Collectors.toCollection(ArrayList::new));
        final Map<String, Aeropuerto> idxAeropuertos = new HashMap<>();
        for (final Aeropuerto a : aeropuertosCopia) {
            if (a != null && a.getIdAeropuerto() != null) idxAeropuertos.put(a.getIdAeropuerto(), a);
        }

        final LocalDateTime ultimoTiempoSim = sesion.getUltimoTiempoSim();
        if (ultimoTiempoSim != null && ultimoTiempoSim.isBefore(inicioVentana)) {
            sesion.proyectarEstadoEnCopias(idxVuelos, idxRutas, idxAeropuertos,
                    ultimoTiempoSim, inicioVentana,
                    sistemaConfiguracion.getUmbralConfirmacionMinutos());
        }

        final ArrayList<VueloInstancia> vuelosFiltrados = vuelosInstanciaCopia.stream()
                .filter(v -> v != null && v.getFechaSalida() != null
                        && !v.getFechaSalida().isBefore(inicioVentana)
                        && !v.getFechaSalida().isAfter(finVentana.plusDays(2)))
                .collect(Collectors.toCollection(ArrayList::new));

        final InstanciaProblema instancia = new InstanciaProblema(
                "SIM-" + sesion.getSessionId() + "-" + windowId,
                pendientes,
                new ArrayList<>(sesion.getVuelosProgramados()),
                vuelosFiltrados,
                aeropuertosCopia
        );
        instancia.setFechaEvaluacion(inicioVentana);
        instancia.setRutasComprometidas(rutasComprometidas);
        instancia.setOcupacionBaseAeropuerto(construirOcupacionBaseAeropuerto(sesion.getAeropuertos()));
        instancia.setEventosBaseAeropuerto(ALNSUtil.construirEventosBase(
                rutasComprometidas, instancia, sesion.getMaletasPorId()));
        return instancia;
    }

    private Map<String, Integer> construirOcupacionBaseAeropuerto(final java.util.List<Aeropuerto> aeropuertos) {
        final Map<String, Integer> ocupacion = new HashMap<>();
        if (aeropuertos == null) {
            return ocupacion;
        }
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto != null && aeropuerto.getIdAeropuerto() != null) {
                ocupacion.put(aeropuerto.getIdAeropuerto(), Math.max(0, aeropuerto.getMaletasActuales()));
            }
        }
        return ocupacion;
    }

    private void limpiarSesion(final SimulacionSesion sesion) {
        cancelarTarea(sesion);
        sesion.finalizarPlanificacion();
        sesion.marcarPlanValido();
        sesion.limpiarReplanPendiente();
        sesion.limpiarDatos();
    }

    private void cancelarTarea(final SimulacionSesion sesion) {
        final ScheduledFuture<?> tarea = sesion.getTareaScheduled();
        if (tarea != null && !tarea.isCancelled()) {
            tarea.cancel(false);
        }
        ((ScheduledThreadPoolExecutor) scheduler).purge();
    }
}
