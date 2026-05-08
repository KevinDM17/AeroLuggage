package pe.edu.pucp.aeroluggage.simulacion.DataTransferObject;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class PeriodoTickDTO {

    private int tickActual;
    private String fechaSimulada;
    private int diaActual;
    private int maletasEnTransito;
    private int maletasEntregadas;
    private int maletasRetrasadas;
}
