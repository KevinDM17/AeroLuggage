package pe.edu.pucp.aeroluggage.servicios.query;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.cargador.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.cargador.DatosEntrada;
import pe.edu.pucp.aeroluggage.cargador.GeneradorVueloInstancias;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionInicioResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SimulacionInicioQueryService {

    private static final String AEROPUERTO_FILE_KEY = "AEROPUERTO_FILE_PATH";
    private static final String PLANES_VUELO_FILE_KEY = "PLANES_VUELO_FILE_PATH";
    private static final String ENVIOS_FOLDER_KEY = "ENVIOS_FOLDER_PATH";
    private static final String DEFAULT_AEROPUERTOS_PATH = "datos/Aeropuertos.txt";
    private static final String DEFAULT_PLANES_VUELO_PATH = "datos/planes_vuelo.txt";
    private static final String DEFAULT_ENVIOS_PATH = "datos/Envios";
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final long MAX_DIAS_VUELOS_INSTANCIAS = 30L;

    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private final SimulacionSnapshotService snapshotService;

    public SimulacionInicioQueryService(final SimulacionSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
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

    private void ensureSnapshotLoaded(final SimulacionSesion sesion) {
        if (sesion.hasSnapshotData()) {
            return;
        }
        synchronized (sesion) {
            if (sesion.hasSnapshotData()) {
                return;
            }

            final Path aeropuertosPath = resolveResourcePath(AEROPUERTO_FILE_KEY, DEFAULT_AEROPUERTOS_PATH);
            final Path vuelosPath = resolveResourcePath(PLANES_VUELO_FILE_KEY, DEFAULT_PLANES_VUELO_PATH);
            final Path enviosPath = resolveEnviosPath();

            final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(aeropuertosPath);
            final Map<String, Aeropuerto> indiceAeropuertos = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
            final LocalDate fechaInicio = sesion.getFechaInicio();
            final LocalDate fechaFin = fechaInicio.plusDays(Math.max(0L, sesion.getTotalDias() - 1L));
            final long diasVuelos = Math.min(sesion.getTotalDias() - 1L, MAX_DIAS_VUELOS_INSTANCIAS);
            final LocalDate fechaFinVuelos = fechaInicio.plusDays(Math.max(0L, diasVuelos));
            final ArrayList<VueloProgramado> vuelosProgramados = CargadorDatosPrueba.cargarVuelosProgramados(
                    vuelosPath,
                    indiceAeropuertos,
                    fechaInicio,
                    sesion.getTotalDias()
            );
            final ArrayList<VueloInstancia> vuelosInstancia = new ArrayList<>(GeneradorVueloInstancias.generar(
                    vuelosProgramados,
                    fechaInicio,
                    fechaFinVuelos
            ));
            log.info("[AeroLuggage/Simulacion] - SNAPSHOT: sessionId={}, totalDias={}, diasVuelos={}, vuelosInstancia={}",
                    sesion.getSessionId(), sesion.getTotalDias(), diasVuelos + 1, vuelosInstancia.size());
            final DatosEntrada datosEntrada = CargadorDatosPrueba.cargarEnviosEnRango(
                    enviosPath,
                    indiceAeropuertos,
                    fechaInicio,
                    fechaFin
            );
            final ArrayList<Pedido> pedidos = new ArrayList<>(datosEntrada.getPedidos());
            pedidos.sort(Comparator.comparing(Pedido::getFechaRegistro).thenComparing(Pedido::getIdPedido));
            final ArrayList<Maleta> maletas = new ArrayList<>(datosEntrada.getMaletas());
            maletas.sort(Comparator.comparing(Maleta::getFechaRegistro).thenComparing(Maleta::getIdMaleta));

            sesion.setSnapshotData(aeropuertos, vuelosProgramados, vuelosInstancia, pedidos, maletas, new ArrayList<>());
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

    private Path resolveResourcePath(final String envKey, final String defaultPath) {
        return resolveAnyResourcePath(dotenv.get(envKey, defaultPath), defaultPath);
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
}
