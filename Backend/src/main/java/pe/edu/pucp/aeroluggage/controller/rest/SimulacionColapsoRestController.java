package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionIniciarRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;

@Slf4j
@RestController
@RequestMapping("/api/simulacion/colapso")
@RequiredArgsConstructor
public class SimulacionColapsoRestController {

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;

    @PostMapping("/iniciar")
    public SimulacionEstadoDTO iniciar(@RequestBody final SimulacionIniciarRequest params) {
        return sesionManager.iniciarColapso(params, broker);
    }
}
