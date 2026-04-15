package pe.edu.pucp.aeroluggage.algorithms.aco;

import pe.edu.pucp.aeroluggage.domain.Maleta;

import java.util.Date;

public class MaletaPendienteACO {
    private Maleta maleta;
    private String idAeropuertoOrigen;
    private String idAeropuertoDestino;
    private Date tiempoDisponible;
    private Date plazoMaximoEntrega;
    private int cantidad;

    public MaletaPendienteACO() {
    }

    public MaletaPendienteACO(final Maleta maleta, final String idAeropuertoOrigen, final String idAeropuertoDestino,
        final Date tiempoDisponible, final Date plazoMaximoEntrega, final int cantidad) {
        this.maleta = maleta;
        this.idAeropuertoOrigen = idAeropuertoOrigen;
        this.idAeropuertoDestino = idAeropuertoDestino;
        this.tiempoDisponible = tiempoDisponible;
        this.plazoMaximoEntrega = plazoMaximoEntrega;
        this.cantidad = cantidad;
    }

    public Maleta getMaleta() {
        return maleta;
    }

    public void setMaleta(final Maleta maleta) {
        this.maleta = maleta;
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

    public Date getTiempoDisponible() {
        return tiempoDisponible;
    }

    public void setTiempoDisponible(final Date tiempoDisponible) {
        this.tiempoDisponible = tiempoDisponible;
    }

    public Date getPlazoMaximoEntrega() {
        return plazoMaximoEntrega;
    }

    public void setPlazoMaximoEntrega(final Date plazoMaximoEntrega) {
        this.plazoMaximoEntrega = plazoMaximoEntrega;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(final int cantidad) {
        this.cantidad = cantidad;
    }

    @Override
    public String toString() {
        return "MaletaPendienteACO{"
            + "maleta=" + maleta
            + ", idAeropuertoOrigen='" + idAeropuertoOrigen + '\''
            + ", idAeropuertoDestino='" + idAeropuertoDestino + '\''
            + ", tiempoDisponible=" + tiempoDisponible
            + ", plazoMaximoEntrega=" + plazoMaximoEntrega
            + ", cantidad=" + cantidad
            + '}';
    }
}
