package pe.edu.pucp.aeroluggage.dto.rest.simulacion;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class VueloInstanciaResponse {

    private String idVueloInstancia;
    private String codigo;
    private String aeropuertoOrigen;
    private String aeropuertoDestino;
    private String fechaSalida;
    private String fechaLlegada;
    private int capacidadMaxima;
    private int capacidadDisponible;
    private int capacidadUsada;
    private String estado;
}
