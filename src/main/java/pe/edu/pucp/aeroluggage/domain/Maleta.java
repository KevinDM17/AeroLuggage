package pe.edu.pucp.aeroluggage.domain;

public class Maleta {
    private String idMaleta;
    private String idEnvio;
    private String estado;

    public Maleta() {
    }

    public Maleta(final String idMaleta, final String idEnvio, final String estado) {
        this.idMaleta = idMaleta;
        this.idEnvio = idEnvio;
        this.estado = estado;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public String getIdEnvio() {
        return idEnvio;
    }

    public void setIdEnvio(final String idEnvio) {
        this.idEnvio = idEnvio;
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
            + ", idEnvio='" + idEnvio + '\''
            + ", estado='" + estado + '\''
            + '}';
    }
}
