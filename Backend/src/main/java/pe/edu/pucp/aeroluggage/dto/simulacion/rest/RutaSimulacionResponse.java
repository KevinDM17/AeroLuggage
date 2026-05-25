package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(setterPrefix = "with")
public class RutaSimulacionResponse {

    private String idRuta;
    private String idMaleta;
    private double plazoMaximoDias;
    private double duracion;
    private String estado;
    private List<RutaVueloResponse> vuelos;
}
