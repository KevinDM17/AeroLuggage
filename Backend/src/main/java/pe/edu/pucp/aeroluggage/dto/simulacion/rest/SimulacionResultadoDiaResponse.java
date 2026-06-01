package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionResultadoDiaResponse {
    private String fecha;
    private int totalMaletas;
    private int maletasConRuta;
    private int maletasEntregadas;
    private int maletasSinRuta;
}
