package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionSesionResumenDTO {

    private String sessionId;
    private String tipo;
    private String fechaInicio;
    private String horaInicio;
    private String simTimeUtc;
    private int tickActual;
    private long elapsedRealSegundos;
    private long elapsedSimMinutos;
    private boolean activa;
    private int totalDias;
}
