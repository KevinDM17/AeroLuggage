package pe.edu.pucp.aeroluggage.dto.simulacion.ws;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class EventoOcupacionDTO {

    private String tipo;
    private int cantidad;
    private String aeropuerto;
    private String vuelo;
}
