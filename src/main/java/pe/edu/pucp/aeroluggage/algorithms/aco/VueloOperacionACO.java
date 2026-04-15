package pe.edu.pucp.aeroluggage.algorithms.aco;

import pe.edu.pucp.aeroluggage.domain.Vuelo;

public class VueloOperacionACO {
    private Vuelo vuelo;
    private String idAeropuertoOrigen;
    private String idAeropuertoDestino;
    private boolean disponible;

    public VueloOperacionACO() {
    }

    public VueloOperacionACO(final Vuelo vuelo, final String idAeropuertoOrigen, final String idAeropuertoDestino,
        final boolean disponible) {
        this.vuelo = vuelo;
        this.idAeropuertoOrigen = idAeropuertoOrigen;
        this.idAeropuertoDestino = idAeropuertoDestino;
        this.disponible = disponible;
    }

    public Vuelo getVuelo() {
        return vuelo;
    }

    public void setVuelo(final Vuelo vuelo) {
        this.vuelo = vuelo;
    }

    public String getIdAeropuertoOrigen() {
        return idAeropuertoOrigen;
    }

    public void setIdAeropuertoOrigen(final String idAeropuertoOrigen) {
        this.idAeropuertoOrigen = idAeropuertoOrigen;
    }

    public String getIdAeropuertoDestino() {
        return idAeropuertoDestino;
    }

    public void setIdAeropuertoDestino(final String idAeropuertoDestino) {
        this.idAeropuertoDestino = idAeropuertoDestino;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(final boolean disponible) {
        this.disponible = disponible;
    }

    @Override
    public String toString() {
        return "VueloOperacionACO{"
            + "vuelo=" + vuelo
            + ", idAeropuertoOrigen='" + idAeropuertoOrigen + '\''
            + ", idAeropuertoDestino='" + idAeropuertoDestino + '\''
            + ", disponible=" + disponible
            + '}';
    }
}
