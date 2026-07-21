package pe.edu.pucp.aeroluggage.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import pe.edu.pucp.aeroluggage.simulacion.OperacionesDiaADiaService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;
    private final OperacionesDiaADiaService diaADiaService;

    @EventListener
    public void manejarConexion(final SessionConnectEvent event) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("[AeroLuggage/WebSocket] CONNECT: wsSessionId={}, timestamp={}",
                accessor.getSessionId(), Instant.now());
    }

    @EventListener
    public void manejarSuscripcion(final SessionSubscribeEvent event) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("[AeroLuggage/WebSocket] SUBSCRIBE: wsSessionId={}, destination={}",
                accessor.getSessionId(), accessor.getDestination());
    }

    @EventListener
    public void manejarDesconexion(final SessionDisconnectEvent event) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        final String wsSessionId = accessor.getSessionId();
        log.info("[AeroLuggage/WebSocket] DISCONNECT: wsSessionId={}, timestamp={}",
                wsSessionId, Instant.now());
        sesionManager.limpiarPorWsSession(wsSessionId, broker);
        diaADiaService.desregistrarCliente(wsSessionId);
    }
}
