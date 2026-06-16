package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class VueloProgramadoResponse {

    private String idVueloProgramado;
    private String codigo;
    private String horaSalida;
    private String horaLlegada;
    private int capacidadBase;
    private String idAeropuertoOrigen;
    private String idAeropuertoDestino;
    private String nombreCiudadDestino;
    private String continenteDestino;
    private int husoOrigen;
    private int husoDestino;
}
