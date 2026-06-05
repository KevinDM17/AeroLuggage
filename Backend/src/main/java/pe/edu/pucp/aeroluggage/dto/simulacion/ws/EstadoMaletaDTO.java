package pe.edu.pucp.aeroluggage.dto.simulacion.ws;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class EstadoMaletaDTO {

    private String id;
    private int e;
}
