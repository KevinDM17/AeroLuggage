package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Envios del panel agrupados por situacion:
 *  - planificados:          por transportar (req 29)
 *  - enVuelos:              actualmente en vuelo (req 30)
 *  - entregadosUltimas4h:   entregados en las ultimas 4 horas simuladas (req 31)
 */
@Getter
@Builder(setterPrefix = "with")
public class EnviosPanelResponse {

    private List<EnvioPanelResponse> planificados;
    private List<EnvioPanelResponse> enVuelos;
    private List<EnvioPanelResponse> entregadosUltimas4h;
}
