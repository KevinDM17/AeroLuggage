package pe.edu.pucp.aeroluggage.simulacion;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.cargador.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.cargador.DatosEntrada;
import pe.edu.pucp.aeroluggage.cargador.GeneradorVueloInstancias;
import pe.edu.pucp.aeroluggage.config.SimulacionParams;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

@Slf4j
@Service
public class SimulacionBootstrapService {

    private static final String AEROPUERTO_FILE_KEY = "AEROPUERTO_FILE_PATH";
    private static final String PLANES_VUELO_FILE_KEY = "PLANES_VUELO_FILE_PATH";
    private static final String ENVIOS_FOLDER_KEY = "ENVIOS_FOLDER_PATH";
    private static final String DEFAULT_AEROPUERTOS_PATH = "datos/Aeropuertos.txt";
    private static final String DEFAULT_PLANES_VUELO_PATH = "datos/planes_vuelo.txt";
    private static final String DEFAULT_ENVIOS_PATH = "datos/Envios";
    private static final long MAX_DIAS_VUELOS_INSTANCIAS = 30L;

    private final Dotenv dotenv;
    private final SimulacionSnapshotService snapshotService;
    private final SimulacionParams simulacionParams;

    public SimulacionBootstrapService(final Dotenv dotenv,
                                      final SimulacionSnapshotService snapshotService,
                                      final SimulacionParams simulacionParams) {
        this.dotenv = dotenv;
        this.snapshotService = snapshotService;
        this.simulacionParams = simulacionParams;
    }

    public void ensureSnapshotLoaded(final SimulacionSesion sesion) {
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
            final long diasVuelos = Math.min(sesion.getTotalDias() - 1L, simulacionParams.getMaxDiasVuelosInstancias());
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
                configured.replace("envÃƒÆ’Ã‚Â­os", "Envios").replace("envÃƒÂ­os", "Envios").replace("envios", "Envios"),
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
}
