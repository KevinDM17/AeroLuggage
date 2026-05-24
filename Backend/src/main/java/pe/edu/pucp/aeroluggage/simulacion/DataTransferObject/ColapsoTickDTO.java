package pe.edu.pucp.aeroluggage.simulacion.DataTransferObject;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class ColapsoTickDTO {

    private int tickActual;
    private String fechaSimulada;
    private int maletasEnTransito;
    private int maletasEntregadas;
    private int maletasRetrasadas;
    private int aeropuertosSaturados;
    private double porcentajeSaturacion;
    private boolean colapsoDetectado;
}
