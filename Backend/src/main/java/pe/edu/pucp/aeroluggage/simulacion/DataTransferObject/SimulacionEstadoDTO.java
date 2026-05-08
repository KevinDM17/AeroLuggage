package pe.edu.pucp.aeroluggage.simulacion.DataTransferObject;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionEstadoDTO {

    private String sessionId;
    private String estado;
    private String mensaje;
}
