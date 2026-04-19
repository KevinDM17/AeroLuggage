package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.Collections;
import java.util.List;

public final class RutaCandidata {
    private final List<String> idVuelos;
    private final long duracionMinutos;
    private final boolean cumpleLlegadaADestino;

    public RutaCandidata(final List<String> idVuelos, final long duracionMinutos,
        final boolean cumpleLlegadaADestino) {
        this.idVuelos = idVuelos == null ? Collections.emptyList() : List.copyOf(idVuelos);
        this.duracionMinutos = duracionMinutos;
        this.cumpleLlegadaADestino = cumpleLlegadaADestino;
    }

    public List<String> getIdVuelos() {
        return idVuelos;
    }

    public long getDuracionMinutos() {
        return duracionMinutos;
    }

    public boolean isCumpleLlegadaADestino() {
        return cumpleLlegadaADestino;
    }

    public int longitud() {
        return idVuelos.size();
    }
}
