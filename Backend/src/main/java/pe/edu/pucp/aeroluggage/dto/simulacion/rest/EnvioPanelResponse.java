package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Un envio (pedido) para el panel de envios, enriquecido con la(s) UT (vuelos)
 * que lo transportan y los aeropuertos por los que pasa su ruta, para poder
 * indicar origen/destino/UT/cantidad y filtrar por tramo o ruta.
 */
@Getter
@Builder(setterPrefix = "with")
public class EnvioPanelResponse {

    private String id;
    private String origin;
    private String dest;
    private int bags;                 // cantidad de productos (maletas)
    private List<String> uts;         // codigos de vuelo (UT) del itinerario
    private List<String> origenesRuta;// aeropuertos de salida de algun tramo (para filtrar por origen)
    private List<String> destinosRuta;// aeropuertos de llegada de algun tramo (para filtrar por destino)
    private String horaEntrega;       // solo para entregados (ISO local date-time)
}
