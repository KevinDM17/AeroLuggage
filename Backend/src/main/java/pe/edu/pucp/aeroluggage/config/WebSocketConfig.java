package pe.edu.pucp.aeroluggage.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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
    private final SimulacionSubscribeInterceptor subscribeInterceptor;
    private final OperationsWebSocketInterceptor operationsInterceptor;
    private final SimulacionDiaADiaParams params;

    public WebSocketConfig(final Dotenv dotenv,
                           final SimulacionSubscribeInterceptor subscribeInterceptor,
                           final OperationsWebSocketInterceptor operationsInterceptor,
                           final SimulacionDiaADiaParams params) {
        this.dotenv = dotenv;
        this.subscribeInterceptor = subscribeInterceptor;
        this.operationsInterceptor = operationsInterceptor;
        this.params = params;
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
        final long hb = params.getHeartbeatMs();
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{hb, hb})
                .setTaskScheduler(heartBeatScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler heartBeatScheduler() {
        final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureClientInboundChannel(final ChannelRegistration registration) {
        registration.interceptors(subscribeInterceptor, operationsInterceptor);
    }

    @Override
    public void configureWebSocketTransport(final WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(50 * 1024 * 1024);
        registration.setSendBufferSizeLimit(50 * 1024 * 1024);
        registration.setSendTimeLimit(60 * 1000);
    }
}
