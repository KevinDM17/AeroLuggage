package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

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
