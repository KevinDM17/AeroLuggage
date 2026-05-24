package pe.edu.pucp.aeroluggage.dto.rest.simulacion;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class PedidoSimulacionResponse {

    private String id;
    private String clientId;
    private String origin;
    private String dest;
    private int bags;
    private String date;
    private String time;
    private String status;
}
