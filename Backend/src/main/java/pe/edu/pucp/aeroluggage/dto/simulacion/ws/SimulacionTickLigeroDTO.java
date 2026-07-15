package pe.edu.pucp.aeroluggage.dto.simulacion.ws;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionTickLigeroDTO {

    private String type;
    private int tick;
    private String simTime;
    private String ventanaActual;
    private long stateVersion;
    private int maletasEnTransito;
    private int maletasEntregadas;
    private int maletasRetrasadas;
    private int maletasNoAsignadas;
    private int vuelosActivos;
    private int capacidadLibrePct;
    private List<EstadoMaletaDTO> estadosMaletas;
    private List<EstadoRutaDTO> estadosRutas;
    private List<EstadoVueloDTO> estadosVuelos;
    private List<OcupacionAeropuertoDTO> aeropuertos;
    private List<EventoOcupacionDTO> eventosOcupacion;
}
