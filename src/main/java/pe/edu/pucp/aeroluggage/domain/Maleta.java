package pe.edu.pucp.aeroluggage.domain;

public class Maleta {
    private String idMaleta;
    private double peso;
    private double largo;
    private double ancho;
    private double alto;
    private String estado;

    public Maleta() {
    }

    public Maleta(final String idMaleta, final double peso, final double largo, final double ancho,
        final double alto, final String estado) {
        this.idMaleta = idMaleta;
        this.peso = peso;
        this.largo = largo;
        this.ancho = ancho;
        this.alto = alto;
        this.estado = estado;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public double getPeso() {
        return peso;
    }

    public void setPeso(final double peso) {
        this.peso = peso;
    }

    public double getLargo() {
        return largo;
    }

    public void setLargo(final double largo) {
        this.largo = largo;
    }

    public double getAncho() {
        return ancho;
    }

    public void setAncho(final double ancho) {
        this.ancho = ancho;
    }

    public double getAlto() {
        return alto;
    }

    public void setAlto(final double alto) {
        this.alto = alto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "Maleta{"
            + "idMaleta='" + idMaleta + '\''
            + ", peso=" + peso
            + ", largo=" + largo
            + ", ancho=" + ancho
            + ", alto=" + alto
            + ", estado='" + estado + '\''
            + '}';
    }
}
