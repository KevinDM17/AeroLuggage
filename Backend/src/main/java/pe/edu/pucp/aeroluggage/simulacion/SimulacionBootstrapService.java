package pe.edu.pucp.aeroluggage.simulacion;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.config.SimulacionParams;
import pe.edu.pucp.aeroluggage.servicios.ServicioCargaSimulacion;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
public class SimulacionBootstrapService {

    private static final String ENVIOS_FOLDER_KEY = "ENVIOS_FOLDER_PATH";
    private static final String DEFAULT_ENVIOS_PATH = "datos/Envios";

    private final Dotenv dotenv;
    private final SimulacionSnapshotService snapshotService;
    private final ServicioCargaSimulacion servicioCargaSimulacion;

    public SimulacionBootstrapService(final Dotenv dotenv,
                                      final SimulacionSnapshotService snapshotService,
                                      final ServicioCargaSimulacion servicioCargaSimulacion) {
        this.dotenv = dotenv;
        this.snapshotService = snapshotService;
        this.servicioCargaSimulacion = servicioCargaSimulacion;
    }

    public void ensureSnapshotLoaded(final SimulacionSesion sesion) {
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
                configured.replace("env\u00c3\u0192\u00c2\u00ados", "Envios")
                        .replace("env\u00c3\u00ados", "Envios")
                        .replace("envios", "Envios"),
                DEFAULT_ENVIOS_PATH
        );
    }

    private Path resolveAnyResourcePath(final String... candidates) {
        for (final String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            final java.io.File resource = new java.io.File(candidate);
            if (resource.exists()) {
                return resource.toPath();
            }
            final org.springframework.core.io.ClassPathResource classpathResource =
                    new org.springframework.core.io.ClassPathResource(candidate);
            if (classpathResource.exists()) {
                try {
                    return classpathResource.getFile().toPath();
                } catch (final IOException exception) {
                    throw new IllegalStateException(
                            "No se pudo resolver el recurso: " + candidate, exception);
                }
            }
        }
        throw new IllegalStateException("No se encontro ningun recurso valido para la simulacion");
    }
}
