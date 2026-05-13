package pe.edu.pucp.aeroluggage.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String FRONTEND_URL_KEY = "FRONTEND_URL";

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        final String frontendUrl = dotenv.get(FRONTEND_URL_KEY, "*");

        registry.addMapping("/**")
                .allowedOriginPatterns(frontendUrl)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
