package pe.edu.pucp.aeroluggage.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.SimulacionIniciarDTO;

@Slf4j
@RestController
@RequestMapping("/api/simulacion/periodo")
@RequiredArgsConstructor
public class SimulacionRestController {

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;

    @PostMapping("/iniciar")
    public SimulacionEstadoDTO iniciar(@RequestBody final SimulacionIniciarDTO params) {
        log.info("[AeroLuggage/SimulacionRest] - API-CALL/iniciar: fechaInicio: {}, totalDias: {}, intervaloMs: {}",
                params.getFechaInicio(), params.getTotalDias(), params.getIntervaloTickMs());
        return sesionManager.iniciar(params, broker);
    }
}
