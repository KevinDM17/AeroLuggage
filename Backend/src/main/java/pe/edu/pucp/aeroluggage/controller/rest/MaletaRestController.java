package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.repositorio.MaletaRepositorio;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/maletas")
@RequiredArgsConstructor
public class MaletaRestController {

    private final MaletaRepositorio maletaRepositorio;

    @GetMapping
    public List<MaletaSimulacionResponse> listar() {
        return maletaRepositorio.obtenerTodos().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private MaletaSimulacionResponse toResponse(final Maleta maleta) {
        return MaletaSimulacionResponse.builder()
                .withIdMaleta(maleta.getIdMaleta())
                .withIdPedido(maleta.getPedido() != null ? maleta.getPedido().getIdPedido() : null)
                .withFechaRegistro(maleta.getFechaRegistro() != null
                        ? maleta.getFechaRegistro().toString() : null)
                .withFechaLlegada(maleta.getFechaLlegada() != null
                        ? maleta.getFechaLlegada().toString() : null)
                .withEstado(maleta.getEstado() != null ? maleta.getEstado().name() : null)
                .withUbicacionActual(maleta.getAeropuertoActual())
                .build();
    }
}
