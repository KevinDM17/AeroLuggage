package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.repositorio.PedidoRepositorio;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoRestController {

    private final PedidoRepositorio pedidoRepositorio;

    @GetMapping
    public List<PedidoSimulacionResponse> listar() {
        return pedidoRepositorio.obtenerTodos().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public PedidoSimulacionResponse obtener(@PathVariable final String id) {
        return pedidoRepositorio.obtenerPorId(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pedido no encontrado: " + id));
    }

    private PedidoSimulacionResponse toResponse(final Pedido pedido) {
        final String fecha = pedido.getFechaRegistro() != null ? pedido.getFechaRegistro().toString() : "";
        final String[] parts = fecha.split("T");
        return PedidoSimulacionResponse.builder()
                .withId(pedido.getIdPedido())
                .withClientId("")
                .withOrigin(pedido.getAeropuertoOrigen() != null
                        ? pedido.getAeropuertoOrigen().getIdAeropuerto() : "")
                .withDest(pedido.getAeropuertoDestino() != null
                        ? pedido.getAeropuertoDestino().getIdAeropuerto() : "")
                .withBags(pedido.getCantidadMaletas())
                .withDate(parts.length > 0 ? parts[0] : "")
                .withTime(parts.length > 1 ? parts[1].substring(0, Math.min(5, parts[1].length())) : "")
                .withStatus(pedido.getEstado() != null ? pedido.getEstado().name() : "")
                .build();
    }
}
