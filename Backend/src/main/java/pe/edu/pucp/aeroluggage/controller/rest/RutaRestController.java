package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.repositorio.RutaRepositorio;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rutas")
@RequiredArgsConstructor
public class RutaRestController {

    private final RutaRepositorio rutaRepositorio;

    @GetMapping
    public List<RutaSimulacionResponse> listar() {
        return rutaRepositorio.obtenerTodos().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private RutaSimulacionResponse toResponse(final Ruta ruta) {
        return RutaSimulacionResponse.builder()
                .withIdRuta(ruta.getIdMaleta())
                .withIdMaleta(ruta.getIdMaleta())
                .withPlazoMaximoDias(ruta.getPlazoMaximoDias())
                .withDuracion(ruta.getDuracion())
                .withEstado(ruta.getEstado() != null ? ruta.getEstado().name() : null)
                .withVuelos(List.of())
                .build();
    }
}
