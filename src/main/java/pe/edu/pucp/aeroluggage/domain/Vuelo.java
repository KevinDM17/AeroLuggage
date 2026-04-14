package pe.edu.pucp.aeroluggage.domain;

import java.util.Date;

public class Vuelo {
    private String idVuelo;
    private String codigo;
    private Date fechaSalida;
    private Date fechaLlegada;
    private int capacidadMaxima;
    private int capacidadDisponible;
    private String estado;

    public Vuelo() {
    }

    public Vuelo(final String idVuelo, final String codigo, final Date fechaSalida, final Date fechaLlegada,
        final int capacidadMaxima, final int capacidadDisponible, final String estado) {
        this.idVuelo = idVuelo;
        this.codigo = codigo;
        this.fechaSalida = fechaSalida;
        this.fechaLlegada = fechaLlegada;
        this.capacidadMaxima = capacidadMaxima;
        this.capacidadDisponible = capacidadDisponible;
        this.estado = estado;
    }

    public String getIdVuelo() {
        return idVuelo;
    }

    public void setIdVuelo(final String idVuelo) {
        this.idVuelo = idVuelo;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(final String codigo) {
        this.codigo = codigo;
    }

    public Date getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(final Date fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public Date getFechaLlegada() {
        return fechaLlegada;
    }

    public void setFechaLlegada(final Date fechaLlegada) {
        this.fechaLlegada = fechaLlegada;
    }

    public int getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(final int capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    public int getCapacidadDisponible() {
        return capacidadDisponible;
    }

    public void setCapacidadDisponible(final int capacidadDisponible) {
        this.capacidadDisponible = capacidadDisponible;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    public void cancelar() {
    }

    public void actualizarCapacidad() {
    }

    @Override
    public String toString() {
        return "Vuelo{"
            + "idVuelo='" + idVuelo + '\''
            + ", codigo='" + codigo + '\''
            + ", fechaSalida=" + fechaSalida
            + ", fechaLlegada=" + fechaLlegada
            + ", capacidadMaxima=" + capacidadMaxima
            + ", capacidadDisponible=" + capacidadDisponible
            + ", estado='" + estado + '\''
            + '}';
    }
}
