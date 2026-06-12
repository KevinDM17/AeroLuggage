package pe.edu.pucp.aeroluggage.config;

import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionDiaADiaService;

@Component
public class OperationsWebSocketInterceptor implements ChannelInterceptor {

    private final SimulacionDiaADiaService service;

    public OperationsWebSocketInterceptor(@Lazy final SimulacionDiaADiaService service) {
        this.service = service;
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
        if (destination.startsWith("/topic/operations/")) {
            final String wsSessionId = accessor.getSessionId();
            service.registrarCliente(wsSessionId);
        }
        return message;
    }
}
