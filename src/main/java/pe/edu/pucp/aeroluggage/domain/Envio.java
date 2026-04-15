package pe.edu.pucp.aeroluggage.domain;

import java.util.Date;

public class Envio {
    private String idEnvio;
    private String idAeropuertoSalida;
    private String idAeropuertoLlegada;
    private Date fechaRegistro;
    private int cantidadMaletas;
    private String estado;

    public Envio() {
    }

    public Envio(final String idEnvio, final String idAeropuertoSalida, final String idAeropuertoLlegada,
        final Date fechaRegistro, final int cantidadMaletas, final String estado) {
        this.idEnvio = idEnvio;
        this.idAeropuertoSalida = idAeropuertoSalida;
        this.idAeropuertoLlegada = idAeropuertoLlegada;
        this.fechaRegistro = fechaRegistro;
        this.cantidadMaletas = cantidadMaletas;
        this.estado = estado;
    }

    public String getIdEnvio() {
        return idEnvio;
    }

    public void setIdEnvio(final String idEnvio) {
        this.idEnvio = idEnvio;
    }

    public String getIdAeropuertoSalida() {
        return idAeropuertoSalida;
    }

    public void setIdAeropuertoSalida(final String idAeropuertoSalida) {
        this.idAeropuertoSalida = idAeropuertoSalida;
    }

    public String getIdAeropuertoLlegada() {
        return idAeropuertoLlegada;
    }

    public void setIdAeropuertoLlegada(final String idAeropuertoLlegada) {
        this.idAeropuertoLlegada = idAeropuertoLlegada;
    }

    public Date getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(final Date fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public int getCantidadMaletas() {
        return cantidadMaletas;
    }

    public void setCantidadMaletas(final int cantidadMaletas) {
        this.cantidadMaletas = cantidadMaletas;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    public void registrarEnvio() {
    }

    @Override
    public String toString() {
        return "Envio{"
            + "idEnvio='" + idEnvio + '\''
            + ", idAeropuertoSalida='" + idAeropuertoSalida + '\''
            + ", idAeropuertoLlegada='" + idAeropuertoLlegada + '\''
            + ", fechaRegistro=" + fechaRegistro
            + ", cantidadMaletas=" + cantidadMaletas
            + ", estado='" + estado + '\''
            + '}';
    }
}
