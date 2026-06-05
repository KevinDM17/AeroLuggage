package pe.edu.pucp.aeroluggage.dto.simulacion.ws;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class EstadoVueloDTO {

    private String id;
    private int e;
    private int cap;
}
