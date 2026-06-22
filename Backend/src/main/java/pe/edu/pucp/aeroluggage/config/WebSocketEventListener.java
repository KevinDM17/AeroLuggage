package pe.edu.pucp.aeroluggage.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import pe.edu.pucp.aeroluggage.simulacion.OperacionesDiaADiaService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;
    private final OperacionesDiaADiaService diaADiaService;

    @EventListener
    public void manejarDesconexion(final SessionDisconnectEvent event) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        final String wsSessionId = accessor.getSessionId();
        log.info("[AeroLuggage/WebSocket] - DESCONEXION: wsSessionId: {}", wsSessionId);
        sesionManager.limpiarPorWsSession(wsSessionId, broker);
        diaADiaService.desregistrarCliente(wsSessionId);
    }
}
