package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PedidoRequest {

    private String idPedido;
    private String idAeropuertoOrigen;
    private String idAeropuertoDestino;
    private int cantidadMaletas;
    private String fechaRegistro;
}
