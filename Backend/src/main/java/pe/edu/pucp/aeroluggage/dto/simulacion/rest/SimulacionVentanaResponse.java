package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionVentanaResponse {

    private String windowId;
    private String startUtc;
    private String endUtc;
    private String status;
    private long generation;
}
