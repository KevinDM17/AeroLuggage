package pe.edu.pucp.aeroluggage.algorithms.aco;

public class ArcoACO {
    private String idAeropuertoOrigen;
    private String idAeropuertoDestino;
    private String idVuelo;

    public ArcoACO() {
    }

    public ArcoACO(final String idAeropuertoOrigen, final String idAeropuertoDestino, final String idVuelo) {
        this.idAeropuertoOrigen = idAeropuertoOrigen;
        this.idAeropuertoDestino = idAeropuertoDestino;
        this.idVuelo = idVuelo;
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

    public String getIdVuelo() {
        return idVuelo;
    }

    public void setIdVuelo(final String idVuelo) {
        this.idVuelo = idVuelo;
    }

    @Override
    public String toString() {
        return "ArcoACO{"
            + "idAeropuertoOrigen='" + idAeropuertoOrigen + '\''
            + ", idAeropuertoDestino='" + idAeropuertoDestino + '\''
            + ", idVuelo='" + idVuelo + '\''
            + '}';
    }
}
