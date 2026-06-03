package pe.edu.pucp.aeroluggage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vuelos")
@PropertySource(value = "classpath:system_params.yml", factory = YamlPropertySourceFactory.class)
public class SistemaConfiguracion {

    private int umbralConfirmacionMinutos = 30;

    public int getUmbralConfirmacionMinutos() {
        return umbralConfirmacionMinutos;
    }

    public void setUmbralConfirmacionMinutos(final int umbralConfirmacionMinutos) {
        this.umbralConfirmacionMinutos = umbralConfirmacionMinutos;
    }
}
