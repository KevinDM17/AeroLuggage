package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VueloProgramadoRequest {

    private String idVueloProgramado;
    private String codigo;
    private String horaSalida;
    private String horaLlegada;
    private int capacidadBase;
    private String idAeropuertoOrigen;
    private String idAeropuertoDestino;
}
