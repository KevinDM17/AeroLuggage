package pe.edu.pucp.aeroluggage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "negocio")
@PropertySource(value = "classpath:system_params.yml", factory = YamlPropertySourceFactory.class)
public class NegocioConfig {

    private long minutosConexion = 10L;
    private long minutosRecojo = 15L;
    private int umbralConfirmacionMinutos = 60;
}
