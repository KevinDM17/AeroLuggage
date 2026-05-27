package pe.edu.pucp.aeroluggage.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String FRONTEND_URL_KEY = "FRONTEND_URL";
    private static final String DEFAULT_FRONTEND_ORIGINS = "http://localhost:5173";

    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
        final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        final String frontendUrl = dotenv.get(FRONTEND_URL_KEY, DEFAULT_FRONTEND_ORIGINS);
        final String[] allowedOrigins = Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).withSockJS();
    }

    @Override
    public void configureMessageBroker(final MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureWebSocketTransport(final WebSocketTransportRegistration registration) {
        // Limites generosos: el tick de simulacion incluye snapshots de
        // pedidos/maletas/rutas que pueden crecer hasta decenas de MB en
        // periodos largos. SimulacionPeriodoService ya throttlea esas listas
        // pesadas, pero dejamos espacio para picos sin tumbar la sesion.
        registration.setMessageSizeLimit(32 * 1024 * 1024);
        registration.setSendBufferSizeLimit(64 * 1024 * 1024);
        // Si el cliente no consume rapido y el buffer se llena, mejor cerrar
        // mas tarde — el default era 10s.
        registration.setSendTimeLimit(30 * 1000);
    }
}
