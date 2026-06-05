package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionBaseResponse {

    private String sessionId;
    private String fechaInicio;
    private int totalDias;
    private int windowSizeMinutes;
    private int windowSpacingMinutes;
    private long duracionDiaSimuladoMs;
    private String primeraVentana;
    private List<AeropuertoResponse> aeropuertos;
}
