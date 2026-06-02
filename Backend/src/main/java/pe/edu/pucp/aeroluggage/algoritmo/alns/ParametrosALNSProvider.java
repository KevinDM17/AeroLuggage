package pe.edu.pucp.aeroluggage.algoritmo.alns;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Slf4j
@Component
public class ParametrosALNSProvider {

    private static final Path PARAMS_FILE = Path.of("experimental", "parametros", "test_params.txt");

    public ParametrosALNS obtener() {
        final ParametrosALNS parametros = ParametrosALNS.porDefecto();
        if (!Files.isRegularFile(PARAMS_FILE)) {
            log.warn("[AeroLuggage/ALNS] - PARAMS: no se encontro {}, se usaran valores por defecto",
                    PARAMS_FILE.toAbsolutePath());
            return parametros;
        }

        final Properties props = new Properties();
        try (InputStream is = Files.newInputStream(PARAMS_FILE)) {
            props.load(is);
        } catch (final IOException exception) {
            log.warn("[AeroLuggage/ALNS] - PARAMS: error leyendo {}, se usaran valores por defecto: {}",
                    PARAMS_FILE.toAbsolutePath(), exception.getMessage());
            return parametros;
        }

        parametros.setMaxIteraciones(intParam(props, "alns.maxIteraciones", parametros.getMaxIteraciones()));
        parametros.setMaxIteracionesSinMejora(
                intParam(props, "alns.maxIteracionesSinMejora", parametros.getMaxIteracionesSinMejora()));
        parametros.setTiempoMaximoMs(longParam(props, "alns.tiempoMaximoMs", parametros.getTiempoMaximoMs()));
        parametros.setQMin(intParam(props, "alns.qMin", parametros.getQMin()));
        parametros.setQMax(intParam(props, "alns.qMax", parametros.getQMax()));
        parametros.setQCritical(intParam(props, "alns.qCritical", parametros.getQCritical()));
        parametros.setMaxReintentosRuteo(intParam(props, "alns.maxReintentosRuteo", parametros.getMaxReintentosRuteo()));
        parametros.setMinutosConexion(longParam(props, "alns.minutosConexion", parametros.getMinutosConexion()));
        parametros.setTiempoRecojo(longParam(props, "alns.tiempoRecojo", parametros.getTiempoRecojo()));
        parametros.setUmbralCriticoAeropuerto(
                doubleParam(props, "alns.umbralCriticoAeropuerto", parametros.getUmbralCriticoAeropuerto()));
        parametros.setTemperaturaInicial(
                doubleParam(props, "alns.temperaturaInicial", parametros.getTemperaturaInicial()));
        parametros.setFactorEnfriamiento(
                doubleParam(props, "alns.factorEnfriamiento", parametros.getFactorEnfriamiento()));
        parametros.setSemilla(longParam(props, "alns.semilla", parametros.getSemilla()));
        parametros.setPesoMaletasNoEnrutadas(
                doubleParam(props, "alns.pesoMaletasNoEnrutadas", parametros.getPesoMaletasNoEnrutadas()));
        parametros.setPesoMaletasFueraDePlazo(
                doubleParam(props, "alns.pesoMaletasFueraDePlazo", parametros.getPesoMaletasFueraDePlazo()));
        parametros.setPesoOverflowVuelos(
                doubleParam(props, "alns.pesoOverflowVuelos", parametros.getPesoOverflowVuelos()));
        parametros.setPesoOverflowAeropuertos(
                doubleParam(props, "alns.pesoOverflowAeropuertos", parametros.getPesoOverflowAeropuertos()));
        parametros.setPesoOcupacionPromedioVuelos(
                doubleParam(props, "alns.pesoOcupacionPromedioVuelos", parametros.getPesoOcupacionPromedioVuelos()));
        parametros.setPesoOcupacionPromedioAeropuertos(
                doubleParam(props, "alns.pesoOcupacionPromedioAeropuertos", parametros.getPesoOcupacionPromedioAeropuertos()));
        parametros.setPesoHolgura(doubleParam(props, "alns.pesoHolgura", parametros.getPesoHolgura()));
        parametros.setSegmentoIteraciones(
                intParam(props, "alns.segmentoIteraciones", parametros.getSegmentoIteraciones()));
        parametros.setSigma1(doubleParam(props, "alns.sigma1", parametros.getSigma1()));
        parametros.setSigma2(doubleParam(props, "alns.sigma2", parametros.getSigma2()));
        parametros.setSigma3(doubleParam(props, "alns.sigma3", parametros.getSigma3()));
        parametros.setSigma4(doubleParam(props, "alns.sigma4", parametros.getSigma4()));
        parametros.setRho(doubleParam(props, "alns.rho", parametros.getRho()));
        parametros.setPesoMinimoOperador(
                doubleParam(props, "alns.pesoMinimoOperador", parametros.getPesoMinimoOperador()));
        return parametros;
    }

    private static int intParam(final Properties props, final String key, final int def) {
        final String value = props.getProperty(key);
        return value != null ? Integer.parseInt(value.trim()) : def;
    }

    private static long longParam(final Properties props, final String key, final long def) {
        final String value = props.getProperty(key);
        return value != null ? Long.parseLong(value.trim()) : def;
    }

    private static double doubleParam(final Properties props, final String key, final double def) {
        final String value = props.getProperty(key);
        return value != null ? Double.parseDouble(value.trim()) : def;
    }
}
