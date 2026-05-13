package pe.edu.pucp.aeroluggage.dto.rest.simulacion;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class RutaVueloResponse {

    private String idVueloInstancia;
    private String codigo;
    private String fechaSalida;
    private String fechaLlegada;
    private String aeropuertoOrigen;
    private String aeropuertoDestino;
}
