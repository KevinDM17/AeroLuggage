package pe.edu.pucp.aeroluggage.controller.ws;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionComandoDTO;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SimulacionPeriodoWsController {

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;

    @MessageMapping("/simulacion/periodo/pausar")
    public void pausar(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
        sesionManager.registrarWsSession(wsSessionId, comando.getSessionId());
        sesionManager.pausar(comando.getSessionId(), broker);
    }

    @MessageMapping("/simulacion/periodo/reanudar")
    public void reanudar(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
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

    @MessageMapping("/simulacion/periodo/cancelar-vuelo")
    public void cancelarVuelo(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
        final List<String> idsMaletas = comando.getIdsMaletas();
        log.info("[AeroLuggage/Simulacion] - WS/cancelar-vuelo: sessionId: {}, vuelo: {}, maletas: {}, wsSession: {}",
                comando.getSessionId(), comando.getIdVueloInstancia(), idsMaletas, wsSessionId);
        sesionManager.registrarWsSession(wsSessionId, comando.getSessionId());
        sesionManager.cancelarVuelo(comando.getSessionId(), comando.getIdVueloInstancia(), idsMaletas, broker);
    }

    @MessageMapping("/simulacion/periodo/iniciar-tick")
    public void iniciarTick(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
        log.info("[AeroLuggage/Simulacion] - WS/iniciar-tick: sessionId: {}, wsSession: {}",
                comando.getSessionId(), wsSessionId);
        sesionManager.registrarWsSession(wsSessionId, comando.getSessionId());
        sesionManager.iniciarTicks(comando.getSessionId(), broker);
    }

    @MessageMapping("/simulacion/periodo/ventana-lista")
    public void ventanaLista(final SimulacionComandoDTO comando, final SimpMessageHeaderAccessor accessor) {
        final String wsSessionId = accessor.getSessionId();
        log.info("[AeroLuggage/Simulacion] - WS/ventana-lista: sessionId: {}, wsSession: {}",
                comando.getSessionId(), wsSessionId);
        sesionManager.reconciliarEstado(comando.getSessionId(), broker);
    }
}
