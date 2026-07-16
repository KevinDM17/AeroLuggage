package pe.edu.pucp.aeroluggage.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SimulacionSubscribeInterceptor implements ChannelInterceptor {

    private static final Pattern TOPIC_SIMULACION = Pattern.compile(
            "^/topic/simulacion/([^/]+)(?:/.*)?$");

    private final SimulacionSesionManager sesionManager;
    private final Map<String, String> suscripcionesRegistradas = new ConcurrentHashMap<>();

    public SimulacionSubscribeInterceptor(final SimulacionSesionManager sesionManager) {
        this.sesionManager = sesionManager;
    }

    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        final String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        final Matcher matcher = TOPIC_SIMULACION.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }
        final String sessionId = matcher.group(1);
        final String wsSessionId = accessor.getSessionId();
        final String clave = wsSessionId + "::" + destination;
        final String existente = suscripcionesRegistradas.putIfAbsent(clave, sessionId);
        if (existente != null) {
            return message;
        }
        log.info("[AeroLuggage/WebSocket] - CONEXION: wsSessionId={}, sessionId={}", wsSessionId, sessionId);
        sesionManager.registrarWsSession(wsSessionId, sessionId);
        return message;
    }
}
