package pe.edu.pucp.aeroluggage.servicios.query;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.servicios.ServicioCargaSimulacion;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionInicioResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionAeropuertosResumenResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionResultadoDiaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionResultadoFinalResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionResultadoVentanaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionVentanaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesion;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSnapshotService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionVentana;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SimulacionInicioQueryService {

    private static final String ENVIOS_FOLDER_KEY = "ENVIOS_FOLDER_PATH";
    private static final String DEFAULT_ENVIOS_PATH = "datos/Envios";
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private final SimulacionSnapshotService snapshotService;
    private final ServicioCargaSimulacion servicioCargaSimulacion;

    public SimulacionInicioQueryService(final SimulacionSnapshotService snapshotService,
                                        final ServicioCargaSimulacion servicioCargaSimulacion) {
        this.snapshotService = snapshotService;
        this.servicioCargaSimulacion = servicioCargaSimulacion;
    }

    public SimulacionInicioResponse construirRespuestaInicio(final SimulacionEstadoDTO estado,
                                                             final SimulacionSesion sesion) {
        ensureSnapshotLoaded(sesion);
        snapshotService.recalcularEstadoSesion(sesion);

        final SimulacionVentana currentWindow = sesion.getCurrentWindow().get();
        return SimulacionInicioResponse.builder()
                .withSessionId(estado.getSessionId())
                .withEstado(estado.getEstado())
                .withMensaje(estado.getMensaje())
                .withFechaInicio(sesion.getFechaInicio().toString())
                .withTotalDias(sesion.getTotalDias())
                .withCurrentSimTimeUtc(formatDateTime(sesion.getCurrentSimTimeUtc().get()))
                .withDuracionDiaSimuladoMs(sesion.getDuracionDiaSimuladoMs())
                .withWindowSizeMinutes(sesion.getWindowSizeMinutes())
                .withStateVersion(sesion.getStateVersion().get())
                .withCurrentWindow(mapearVentana(currentWindow))
                .withNextWindow(mapearVentana(sesion.buildNextWindow()))
                .withAeropuertos(snapshotService.mapearAeropuertos(sesion.getAeropuertos()))
                .withVuelosInstancia(snapshotService.mapearVuelosInstancia(sesion.getVuelosInstancia()))
                .build();
    }

    public SimulacionResultadoFinalResponse construirResultadoFinal(final String estado,
                                                                    final String mensaje,
                                                                    final SimulacionSesion sesion) {
        ensureSnapshotLoaded(sesion);
        snapshotService.recalcularEstadoSesion(sesion);
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        final TotalesResultado totales = calcularTotalesResultado(sesion, simTimeUtc);

        return SimulacionResultadoFinalResponse.builder()
                .withSessionId(sesion.getSessionId())
                .withEstado(estado)
                .withMensaje(mensaje)
                .withFechaInicio(sesion.getFechaInicio().toString())
                .withTotalDias(sesion.getTotalDias())
                .withCurrentSimTimeUtc(formatDateTime(simTimeUtc))
                .withStateVersion(sesion.getStateVersion().get())
                .withTotalMaletas(totales.totalMaletas())
                .withMaletasConRuta(totales.maletasConRuta())
                .withMaletasEntregadas(totales.maletasEntregadas())
                .withMaletasSinRuta(totales.maletasSinRuta())
                .withMaletasEnTransito(totales.maletasEnTransito())
                .withCapacidadLibrePct(snapshotService.calcularCapacidadLibrePct(sesion))
                .withResumenPorDia(construirResumenPorDia(sesion, simTimeUtc))
                .withResumenPorVentana(construirResumenPorVentana(sesion))
                .withAeropuertosResumen(construirResumenAeropuertos(sesion))
                .withTotalVuelos(sesion.getVuelosInstancia().size())
                .withVuelosActivos(snapshotService.contarVuelosActivos(sesion))
                .build();
    }

    private void ensureSnapshotLoaded(final SimulacionSesion sesion) {
        if (sesion.hasSnapshotData()) {
            return;
        }
        synchronized (sesion) {
            if (sesion.hasSnapshotData()) {
                return;
            }

            final Path enviosPath = resolveEnviosPath();
            servicioCargaSimulacion.cargarDatosSimulacion(sesion, enviosPath);
            snapshotService.recalcularEstadoSesion(sesion);
            sesion.getStateVersion().incrementAndGet();
        }
    }

    private Path resolveEnviosPath() {
        final String configured = dotenv.get(ENVIOS_FOLDER_KEY, DEFAULT_ENVIOS_PATH);
        return resolveAnyResourcePath(
                configured,
                configured.replace("envÃƒÂ­os", "Envios").replace("envÃ­os", "Envios").replace("envios", "Envios"),
                DEFAULT_ENVIOS_PATH
        );
    }

    private Path resolveAnyResourcePath(final String... candidates) {
        for (final String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            final ClassPathResource resource = new ClassPathResource(candidate);
            if (!resource.exists()) {
                continue;
            }
            try {
                return resource.getFile().toPath();
            } catch (final IOException exception) {
                throw new IllegalStateException("No se pudo resolver el recurso: " + candidate, exception);
            }
        }
        throw new IllegalStateException("No se encontro ningun recurso valido para la simulacion");
    }

    private SimulacionVentanaResponse mapearVentana(final SimulacionVentana ventana) {
        if (ventana == null) {
            return null;
        }
        return SimulacionVentanaResponse.builder()
                .withWindowId(ventana.getWindowId())
                .withStartUtc(formatDateTime(ventana.getStartUtc()))
                .withEndUtc(formatDateTime(ventana.getEndUtc()))
                .withStatus(ventana.getStatus())
                .withGeneration(ventana.getGeneration())
                .build();
    }

    private String formatDateTime(final LocalDateTime value) {
        return value != null ? value.format(ISO_DATE_TIME) : null;
    }

    private TotalesResultado calcularTotalesResultado(final SimulacionSesion sesion, final LocalDateTime simTimeUtc) {
        final Map<String, Boolean> rutaValidaPorMaleta = new HashMap<>();
        for (final var ruta : sesion.getRutas()) {
            if (ruta != null && ruta.getIdMaleta() != null) {
                final boolean rutaValida = ruta.getSubrutas() != null
                        && !ruta.getSubrutas().isEmpty()
                        && ruta.getEstado() != null
                        && ruta.getEstado() != pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta.FALLIDA;
                rutaValidaPorMaleta.putIfAbsent(ruta.getIdMaleta(), rutaValida);
            }
        }
        int totalMaletas = 0;
        int maletasConRuta = 0;
        int maletasEntregadas = 0;
        int maletasSinRuta = 0;
        int maletasEnTransito = 0;
        for (final Maleta maleta : sesion.getMaletas()) {
            if (maleta == null || maleta.getFechaRegistro() == null || maleta.getFechaRegistro().isAfter(simTimeUtc)) {
                continue;
            }
            totalMaletas++;
            final boolean conRuta = rutaValidaPorMaleta.getOrDefault(maleta.getIdMaleta(), false);
            if (conRuta) {
                maletasConRuta++;
            } else {
                maletasSinRuta++;
            }
            if (maleta.getEstado() == pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta.ENTREGADA) {
                maletasEntregadas++;
            }
            if (maleta.getEstado() == pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta.EN_TRANSITO) {
                maletasEnTransito++;
            }
        }
        return new TotalesResultado(totalMaletas, maletasConRuta, maletasEntregadas, maletasSinRuta, maletasEnTransito);
    }

    private List<SimulacionResultadoDiaResponse> construirResumenPorDia(final SimulacionSesion sesion,
                                                                        final LocalDateTime simTimeUtc) {
        final Map<String, Boolean> rutaValidaPorMaleta = new HashMap<>();
        for (final var ruta : sesion.getRutas()) {
            if (ruta != null && ruta.getIdMaleta() != null) {
                final boolean rutaValida = ruta.getSubrutas() != null
                        && !ruta.getSubrutas().isEmpty()
                        && ruta.getEstado() != null
                        && ruta.getEstado() != pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta.FALLIDA;
                rutaValidaPorMaleta.putIfAbsent(ruta.getIdMaleta(), rutaValida);
            }
        }

        final Map<LocalDate, ResumenDiaMutable> porDia = new HashMap<>();
        for (final Maleta maleta : sesion.getMaletas()) {
            if (maleta == null || maleta.getFechaRegistro() == null || maleta.getFechaRegistro().isAfter(simTimeUtc)) {
                continue;
            }
            final LocalDate fecha = maleta.getFechaRegistro().toLocalDate();
            final ResumenDiaMutable resumen = porDia.computeIfAbsent(fecha, ignored -> new ResumenDiaMutable());
            resumen.totalMaletas++;
            final boolean conRuta = rutaValidaPorMaleta.getOrDefault(maleta.getIdMaleta(), false);
            if (conRuta) {
                resumen.maletasConRuta++;
            } else {
                resumen.maletasSinRuta++;
            }
            if (maleta.getEstado() == pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta.ENTREGADA) {
                resumen.maletasEntregadas++;
            }
        }
        return porDia.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> SimulacionResultadoDiaResponse.builder()
                        .withFecha(entry.getKey().toString())
                        .withTotalMaletas(entry.getValue().totalMaletas)
                        .withMaletasConRuta(entry.getValue().maletasConRuta)
                        .withMaletasEntregadas(entry.getValue().maletasEntregadas)
                        .withMaletasSinRuta(entry.getValue().maletasSinRuta)
                        .build())
                .toList();
    }

    private record TotalesResultado(int totalMaletas,
                                    int maletasConRuta,
                                    int maletasEntregadas,
                                    int maletasSinRuta,
                                    int maletasEnTransito) {
    }

    private static final class ResumenDiaMutable {
        private int totalMaletas;
        private int maletasConRuta;
        private int maletasEntregadas;
        private int maletasSinRuta;
    }

    private SimulacionAeropuertosResumenResponse construirResumenAeropuertos(final SimulacionSesion sesion) {
        int totalAeropuertos = 0;
        int capacidadTotal = 0;
        int maletasActualesTotal = 0;
        double sumaOcupacionPct = 0.0D;
        double ocupacionMaximaPct = 0.0D;

        for (final Aeropuerto aeropuerto : sesion.getAeropuertos()) {
            if (aeropuerto == null) {
                continue;
            }
            totalAeropuertos++;
            final int capacidad = Math.max(0, aeropuerto.getCapacidadAlmacen());
            final int maletasActuales = Math.max(0, aeropuerto.getMaletasActuales());
            capacidadTotal += capacidad;
            maletasActualesTotal += maletasActuales;
            final double ocupacionPct = capacidad > 0 ? (100.0D * maletasActuales / capacidad) : 0.0D;
            sumaOcupacionPct += ocupacionPct;
            ocupacionMaximaPct = Math.max(ocupacionMaximaPct, ocupacionPct);
        }

        final double ocupacionPromedioPct = totalAeropuertos > 0 ? sumaOcupacionPct / totalAeropuertos : 0.0D;
        return SimulacionAeropuertosResumenResponse.builder()
                .withTotalAeropuertos(totalAeropuertos)
                .withCapacidadTotalAlmacen(capacidadTotal)
                .withMaletasActualesTotal(maletasActualesTotal)
                .withOcupacionPromedioPct(ocupacionPromedioPct)
                .withOcupacionMaximaPct(ocupacionMaximaPct)
                .build();
    }

    private List<SimulacionResultadoVentanaResponse> construirResumenPorVentana(final SimulacionSesion sesion) {
        return sesion.getResumenesVentana().stream()
                .map(resumen -> SimulacionResultadoVentanaResponse.builder()
                        .withWindowId(resumen.windowId())
                        .withFecha(resumen.startUtc().toLocalDate().toString())
                        .withStartUtc(formatDateTime(resumen.startUtc()))
                        .withEndUtc(formatDateTime(resumen.endUtc()))
                        .withMaletasEvaluadas(resumen.maletasEvaluadas())
                        .withMaletasEnrutadas(resumen.maletasEnrutadas())
                        .withMaletasSinRuta(resumen.maletasSinRuta())
                        .withTiempoPlanificacionMs(resumen.tiempoPlanificacionMs())
                        .build())
                .toList();
    }
}
