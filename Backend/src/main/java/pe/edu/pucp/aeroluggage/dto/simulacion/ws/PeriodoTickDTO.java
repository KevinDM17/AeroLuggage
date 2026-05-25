package pe.edu.pucp.aeroluggage.dto.simulacion.ws;

import lombok.Builder;
import lombok.Getter;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloInstanciaResponse;

import java.util.List;

@Getter
@Builder(setterPrefix = "with")
public class PeriodoTickDTO {

    private int tickActual;
    private String fechaSimulada;
    private int diaActual;
    private String currentSimTimeUtc;
    private String currentWindowId;
    private String currentWindowStartUtc;
    private String currentWindowEndUtc;
    private String currentWindowStatus;
    private long stateVersion;
    private int maletasEnTransito;
    private int maletasEntregadas;
    private int maletasRetrasadas;
    private int maletasNoAsignadas;
    private int vuelosActivos;
    private int capacidadLibrePct;
    private List<AeropuertoResponse> aeropuertos;
    private List<VueloInstanciaResponse> vuelosInstancia;
    private List<PedidoSimulacionResponse> pedidos;
    private List<MaletaSimulacionResponse> maletas;
    private List<RutaSimulacionResponse> rutas;
}
