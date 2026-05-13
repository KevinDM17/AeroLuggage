package pe.edu.pucp.aeroluggage.simulacion.DataTransferObject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SimulacionIniciarDTO {

    private long intervaloTickMs;
    private String fechaInicio;
    private int totalDias;
}
