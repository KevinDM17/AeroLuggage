package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionResultadoVentanaResponse {
    private String windowId;
    private String fecha;
    private String startUtc;
    private String endUtc;
    private int maletasEvaluadas;
    private int maletasEnrutadas;
    private int maletasSinRuta;
}
