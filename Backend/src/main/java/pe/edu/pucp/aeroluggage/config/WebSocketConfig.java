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

    private final Dotenv dotenv;

    public WebSocketConfig(final Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
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
        registration.setMessageSizeLimit(4 * 1024 * 1024);
        registration.setSendBufferSizeLimit(8 * 1024 * 1024);
    }
}
