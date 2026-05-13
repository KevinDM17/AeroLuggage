package pe.edu.pucp.aeroluggage.dto.rest.simulacion;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SimulacionIniciarRequest {

    @JsonAlias("intervaloTickMs")
    private long duracionDiaSimuladoMs;
    private String fechaInicio;
    private int totalDias;
}
