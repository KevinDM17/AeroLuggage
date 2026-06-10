package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Manifiesto de una UT (vuelo): los envios (pedidos) y productos (maletas) que
 * traslada. Se calcula desde las rutas de la sesion (cada ruta lleva una maleta
 * por una secuencia de vuelos), por lo que es la fuente autoritativa para los
 * requisitos de "acceso a envios/productos desde la lista de UT".
 */
@Getter
@Builder(setterPrefix = "with")
public class VueloManifiestoResponse {

    private String idVueloInstancia;
    private String codigo;
    private List<MaletaSimulacionResponse> maletas;
    private List<PedidoSimulacionResponse> pedidos;
}
