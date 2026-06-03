package pe.edu.pucp.aeroluggage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AeroLuggageApplication {

    public static void main(final String[] args) {
        SpringApplication.run(AeroLuggageApplication.class, args);
    }
}
