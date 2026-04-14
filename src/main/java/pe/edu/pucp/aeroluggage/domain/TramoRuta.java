package pe.edu.pucp.aeroluggage.domain;

public class TramoRuta {
    private String idTramo;
    private int orden;
    private double tiempoEstimado;
    private String estado;

    public TramoRuta() {
    }

    public TramoRuta(final String idTramo, final int orden, final double tiempoEstimado, final String estado) {
        this.idTramo = idTramo;
        this.orden = orden;
        this.tiempoEstimado = tiempoEstimado;
        this.estado = estado;
    }

    public String getIdTramo() {
        return idTramo;
    }

    public void setIdTramo(final String idTramo) {
        this.idTramo = idTramo;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(final int orden) {
        this.orden = orden;
    }

    public double getTiempoEstimado() {
        return tiempoEstimado;
    }

    public void setTiempoEstimado(final double tiempoEstimado) {
        this.tiempoEstimado = tiempoEstimado;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "TramoRuta{"
            + "idTramo='" + idTramo + '\''
            + ", orden=" + orden
            + ", tiempoEstimado=" + tiempoEstimado
            + ", estado='" + estado + '\''
            + '}';
    }
}
