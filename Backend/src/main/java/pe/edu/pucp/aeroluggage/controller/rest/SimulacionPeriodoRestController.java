package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.SimulacionIniciarRequest;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.SimulacionInicioResponse;
import pe.edu.pucp.aeroluggage.servicios.query.SimulacionInicioQueryService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.SimulacionEstadoDTO;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/simulacion/periodo")
@RequiredArgsConstructor
public class SimulacionPeriodoRestController {

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;
    private final SimulacionInicioQueryService simulacionInicioQueryService;

    @PostMapping("/iniciar")
    public SimulacionInicioResponse iniciar(@RequestBody final SimulacionIniciarRequest params) {
        log.info("[AeroLuggage/SimulacionRest] - API-CALL/iniciar: fechaInicio: {}, totalDias: {}, duracionDiaSimuladoMs: {}",
                params.getFechaInicio(), params.getTotalDias(), params.getDuracionDiaSimuladoMs());
        final SimulacionEstadoDTO estado = sesionManager.iniciar(params, broker);
        final LocalDate fechaInicio = LocalDate.parse(params.getFechaInicio());
        return simulacionInicioQueryService.construirRespuestaInicio(estado, fechaInicio, params.getTotalDias());
    }
}
