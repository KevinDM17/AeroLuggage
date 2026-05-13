package pe.edu.pucp.aeroluggage.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String FRONTEND_URL_KEY = "FRONTEND_URL";

    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
        final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        final String frontendUrl = dotenv.get(FRONTEND_URL_KEY, "http://localhost:5173");
        registry.addEndpoint("/ws").setAllowedOriginPatterns(frontendUrl).withSockJS();
    }

    @Override
    public void configureMessageBroker(final MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
