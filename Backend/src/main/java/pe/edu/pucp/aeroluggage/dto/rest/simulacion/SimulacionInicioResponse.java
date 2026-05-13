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
    private String currentSimTimeUtc;
    private long duracionDiaSimuladoMs;
    private int windowSizeMinutes;
    private long stateVersion;
    private SimulacionVentanaResponse currentWindow;
    private SimulacionVentanaResponse nextWindow;
    private List<AeropuertoResponse> aeropuertos;
    private List<VueloInstanciaResponse> vuelosInstancia;
    private List<PedidoSimulacionResponse> pedidos;
    private List<MaletaSimulacionResponse> maletas;
    private List<RutaSimulacionResponse> rutas;
}
