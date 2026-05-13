package pe.edu.pucp.aeroluggage.dto.rest.simulacion;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionInicioResponse {

    private String sessionId;
    private String estado;
    private String mensaje;
    private String fechaInicio;
    private int totalDias;
    private List<AeropuertoResponse> aeropuertos;
    private List<VueloInstanciaResponse> vuelosInstancia;
}
