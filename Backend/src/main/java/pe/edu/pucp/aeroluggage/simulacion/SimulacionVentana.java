package pe.edu.pucp.aeroluggage.simulacion;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SimulacionVentana {

    private final String windowId;
    private final LocalDateTime startUtc;
    private final LocalDateTime endUtc;
    private final String status;
    private final long generation;

    public SimulacionVentana(final String windowId,
                             final LocalDateTime startUtc,
                             final LocalDateTime endUtc,
                             final String status,
                             final long generation) {
        this.windowId = windowId;
        this.startUtc = startUtc;
        this.endUtc = endUtc;
        this.status = status;
        this.generation = generation;
    }
}
