package pe.edu.pucp.aeroluggage.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.SimulacionComandoDTO;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SimulacionController {

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;

    @MessageMapping("/simulacion/periodo/pausar")
    public void pausar(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
        log.info("[AeroLuggage/Simulacion] - WS/pausar: sessionId: {}, wsSession: {}",
                comando.getSessionId(), wsSessionId);
        sesionManager.registrarWsSession(wsSessionId, comando.getSessionId());
        sesionManager.pausar(comando.getSessionId(), broker);
    }

    @MessageMapping("/simulacion/periodo/reanudar")
    public void reanudar(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
        log.info("[AeroLuggage/Simulacion] - WS/reanudar: sessionId: {}, wsSession: {}",
                comando.getSessionId(), wsSessionId);
        sesionManager.registrarWsSession(wsSessionId, comando.getSessionId());
        sesionManager.reanudar(comando.getSessionId(), broker);
    }

    @MessageMapping("/simulacion/periodo/detener")
    public void detener(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
        log.info("[AeroLuggage/Simulacion] - WS/detener: sessionId: {}, wsSession: {}",
                comando.getSessionId(), wsSessionId);
        sesionManager.registrarWsSession(wsSessionId, comando.getSessionId());
        sesionManager.detener(comando.getSessionId(), broker);
    }
}
