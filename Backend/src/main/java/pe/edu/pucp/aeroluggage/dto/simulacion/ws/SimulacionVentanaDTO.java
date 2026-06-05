package pe.edu.pucp.aeroluggage.dto.simulacion.ws;

import lombok.Builder;
import lombok.Getter;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloInstanciaResponse;

import java.util.List;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionVentanaDTO {

    private String type;
    private String ventana;
    private List<MaletaSimulacionResponse> maletas;
    private List<PedidoSimulacionResponse> pedidos;
    private List<RutaSimulacionResponse> rutas;
    private List<VueloInstanciaResponse> vuelos;
}
