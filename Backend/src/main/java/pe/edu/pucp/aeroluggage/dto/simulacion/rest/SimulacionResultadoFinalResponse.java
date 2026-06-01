package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionResultadoFinalResponse {
    private String sessionId;
    private String estado;
    private String mensaje;
    private String fechaInicio;
    private int totalDias;
    private String currentSimTimeUtc;
    private long stateVersion;
    private int totalMaletas;
    private int maletasConRuta;
    private int maletasEntregadas;
    private int maletasSinRuta;
    private int maletasEnTransito;
    private int capacidadLibrePct;
    private java.util.List<SimulacionResultadoDiaResponse> resumenPorDia;
    private java.util.List<SimulacionResultadoVentanaResponse> resumenPorVentana;
    private SimulacionAeropuertosResumenResponse aeropuertosResumen;
    private int totalVuelos;
    private int vuelosActivos;
}
