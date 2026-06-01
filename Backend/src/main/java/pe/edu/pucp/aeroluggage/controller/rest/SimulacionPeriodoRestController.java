package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionIniciarRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionInicioResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionResultadoFinalResponse;
import pe.edu.pucp.aeroluggage.servicios.query.SimulacionInicioQueryService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;

import static org.springframework.http.HttpStatus.NOT_FOUND;

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
        return simulacionInicioQueryService.construirRespuestaInicio(
                estado,
                sesionManager.obtenerSesion(estado.getSessionId())
        );
    }

    @GetMapping("/{sessionId}/snapshot")
    public SimulacionInicioResponse snapshot(@PathVariable final String sessionId) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion de simulacion no encontrada: " + sessionId);
        }
        return simulacionInicioQueryService.construirRespuestaInicio(
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado("ACTUALIZADA")
                        .withMensaje("Snapshot de simulacion actualizado")
                        .build(),
                sesion
        );
    }

    @GetMapping("/{sessionId}/resultado-final")
    public SimulacionResultadoFinalResponse resultadoFinal(@PathVariable final String sessionId) {
        final var sesion = sesionManager.obtenerSesionFinalizada(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Resultado final de simulacion no encontrado: " + sessionId);
        }
        return simulacionInicioQueryService.construirResultadoFinal(
                "FINALIZADA",
                "Resultado final de simulacion",
                sesion
        );
    }
}
