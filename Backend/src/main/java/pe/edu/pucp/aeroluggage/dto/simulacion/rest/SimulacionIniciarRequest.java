package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SimulacionIniciarRequest {

    private String fechaInicio;
    private int totalDias;
}
