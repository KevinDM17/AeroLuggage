package pe.edu.pucp.aeroluggage.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String FRONTEND_URL_KEY = "FRONTEND_URL";
    private static final String DEFAULT_FRONTEND_ORIGINS = "http://localhost:5173";

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        final String frontendUrl = dotenv.get(FRONTEND_URL_KEY, DEFAULT_FRONTEND_ORIGINS);
        final String[] allowedOrigins = Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
