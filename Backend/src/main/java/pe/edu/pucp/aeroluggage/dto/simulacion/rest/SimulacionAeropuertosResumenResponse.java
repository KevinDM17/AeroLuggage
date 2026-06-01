package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(setterPrefix = "with")
public class SimulacionAeropuertosResumenResponse {
    private int totalAeropuertos;
    private int capacidadTotalAlmacen;
    private int maletasActualesTotal;
    private double ocupacionPromedioPct;
    private double ocupacionMaximaPct;
}
