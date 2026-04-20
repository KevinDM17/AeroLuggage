package pe.edu.pucp.aeroluggage.algoritmos.common;

import java.time.Duration;

import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

public final class ArcoVuelo {

    private final NodoTiempo origen;
    private final NodoTiempo destino;
    private final VueloInstancia vuelo;
    private final long duracionMinutos;

    public ArcoVuelo(final NodoTiempo origen, final NodoTiempo destino, final VueloInstancia vuelo) {
        this.origen = origen;
        this.destino = destino;
        this.vuelo = vuelo;
        this.duracionMinutos = Duration.between(origen.getInstante(), destino.getInstante()).toMinutes();
    }

    public NodoTiempo getOrigen() {
        return origen;
    }

    public NodoTiempo getDestino() {
        return destino;
    }

    public VueloInstancia getVuelo() {
        return vuelo;
    }

    public long getDuracionMinutos() {
        return duracionMinutos;
    }
}
