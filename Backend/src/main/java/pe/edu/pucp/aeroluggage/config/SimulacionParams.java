package pe.edu.pucp.aeroluggage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulacion")
@PropertySource(value = "classpath:system_params.yml", factory = YamlPropertySourceFactory.class)
public class SimulacionParams {

    private long duracionDiaSimuladoMs = 600000L;
    private Ventana ventana = new Ventana();
    private long tickIntervalMs = 1000L;
    private long maxDiasVuelosInstancias = 30L;

    public long getDuracionDiaSimuladoMs() {
        return duracionDiaSimuladoMs;
    }

    public void setDuracionDiaSimuladoMs(final long duracionDiaSimuladoMs) {
        this.duracionDiaSimuladoMs = duracionDiaSimuladoMs;
    }

    public Ventana getVentana() {
        return ventana;
    }

    public void setVentana(final Ventana ventana) {
        this.ventana = ventana;
    }

    public long getTickIntervalMs() {
        return tickIntervalMs;
    }

    public void setTickIntervalMs(final long tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
    }

    public long getMaxDiasVuelosInstancias() {
        return maxDiasVuelosInstancias;
    }

    public void setMaxDiasVuelosInstancias(final long maxDiasVuelosInstancias) {
        this.maxDiasVuelosInstancias = maxDiasVuelosInstancias;
    }

    public static class Ventana {
        private int tamanioMinutos = 120;
        private int espaciadoMinutos = 120;

        public int getTamanioMinutos() {
            return tamanioMinutos;
        }

        public void setTamanioMinutos(final int tamanioMinutos) {
            this.tamanioMinutos = tamanioMinutos;
        }

        public int getEspaciadoMinutos() {
            return espaciadoMinutos;
        }

        public void setEspaciadoMinutos(final int espaciadoMinutos) {
            this.espaciadoMinutos = espaciadoMinutos;
        }
    }
}
