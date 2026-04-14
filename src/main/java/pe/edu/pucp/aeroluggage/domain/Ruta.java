package pe.edu.pucp.aeroluggage.domain;

public class Ruta {
    private String idRuta;
    private double plazoMaximoDias;
    private String estado;

    public Ruta() {
    }

    public Ruta(final String idRuta, final double plazoMaximoDias, final String estado) {
        this.idRuta = idRuta;
        this.plazoMaximoDias = plazoMaximoDias;
        this.estado = estado;
    }

    public String getIdRuta() {
        return idRuta;
    }

    public void setIdRuta(final String idRuta) {
        this.idRuta = idRuta;
    }

    public double getPlazoMaximoDias() {
        return plazoMaximoDias;
    }

    public void setPlazoMaximoDias(final double plazoMaximoDias) {
        this.plazoMaximoDias = plazoMaximoDias;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    public void calcularPlazo() {
    }

    public void replanificar() {
    }

    @Override
    public String toString() {
        return "Ruta{"
            + "idRuta='" + idRuta + '\''
            + ", plazoMaximoDias=" + plazoMaximoDias
            + ", estado='" + estado + '\''
            + '}';
    }
}
