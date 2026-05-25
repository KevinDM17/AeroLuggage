package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionInicioResponse {

    private String sessionId;
    private String estado;
    private String mensaje;
    private String fechaInicio;
    private int totalDias;
    private String currentSimTimeUtc;
    private long duracionDiaSimuladoMs;
    private int windowSizeMinutes;
    private long stateVersion;
    private SimulacionVentanaResponse currentWindow;
    private SimulacionVentanaResponse nextWindow;
    private List<AeropuertoResponse> aeropuertos;
    private List<VueloInstanciaResponse> vuelosInstancia;
}
