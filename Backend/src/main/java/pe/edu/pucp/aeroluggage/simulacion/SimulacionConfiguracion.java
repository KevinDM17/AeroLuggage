package pe.edu.pucp.aeroluggage.simulacion;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
@Getter
@Component
public class SimulacionConfiguracion {

    private static final String CONFIG_FILE = "sim_params.txt";
    private static final String KEY_DURACION_DIA = "simulacion.duracionDiaSimuladoMs";
    private static final String KEY_TAMANIO_VENTANA = "simulacion.ventana.tamanioMinutos";
    private static final String KEY_ESPACIADO_VENTANA = "simulacion.ventana.espaciadoMinutos";
    private static final String KEY_TICK_INTERVAL = "simulacion.tickIntervalMs";

    private long duracionDiaSimuladoMs;
    private int windowSizeMinutes;
    private int windowSpacingMinutes;
    private long tickIntervalMs;

    @PostConstruct
    private void cargar() {
        final ClassPathResource resource = new ClassPathResource(CONFIG_FILE);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "No se encontro el archivo de configuracion: " + CONFIG_FILE
            );
        }

        final Properties props = new Properties();
        try (InputStream is = resource.getInputStream()) {
            props.load(is);
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Error al leer el archivo de configuracion: " + CONFIG_FILE, exception
            );
        }

        this.duracionDiaSimuladoMs = obtenerLong(props, KEY_DURACION_DIA);
        this.windowSizeMinutes = obtenerInt(props, KEY_TAMANIO_VENTANA);
        this.windowSpacingMinutes = obtenerInt(props, KEY_ESPACIADO_VENTANA);
        this.tickIntervalMs = obtenerLong(props, KEY_TICK_INTERVAL);

        log.info("[AeroLuggage/SimulacionConfig] - CARGADA: duracionDiaSimuladoMs={}, ventana.tamanio={}, ventana.espaciado={}, tickIntervalMs={}",
                this.duracionDiaSimuladoMs, this.windowSizeMinutes, this.windowSpacingMinutes, this.tickIntervalMs);
    }

    private static long obtenerLong(final Properties props, final String key) {
        final String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Parametro faltante en " + CONFIG_FILE + ": " + key
            );
        }
        try {
            return Long.parseLong(value.trim());
        } catch (final NumberFormatException exception) {
            throw new IllegalStateException(
                    "Valor invalido para " + key + " en " + CONFIG_FILE + ": " + value, exception
            );
        }
    }

    private static int obtenerInt(final Properties props, final String key) {
        final String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Parametro faltante en " + CONFIG_FILE + ": " + key
            );
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException exception) {
            throw new IllegalStateException(
                    "Valor invalido para " + key + " en " + CONFIG_FILE + ": " + value, exception
            );
        }
    }
}
