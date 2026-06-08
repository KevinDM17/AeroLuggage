package pe.edu.pucp.aeroluggage.dto.simulacion.ws;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SimulacionComandoDTO {

    private String sessionId;
    private String idVueloInstancia;
    private List<String> idsMaletas;
}
