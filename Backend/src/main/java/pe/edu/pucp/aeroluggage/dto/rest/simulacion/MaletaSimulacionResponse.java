package pe.edu.pucp.aeroluggage.dto.rest.simulacion;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class MaletaSimulacionResponse {

    private String idMaleta;
    private String idPedido;
    private String fechaRegistro;
    private String fechaLlegada;
    private String estado;
    private String ubicacionActual;
}
