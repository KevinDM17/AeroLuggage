package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.LocalTime;

public class VueloProgramado {
    private String idVueloProgramado;
    private String codigo;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private int capacidadMaxima;
    private Aeropuerto aeropuertoOrigen;
    private Aeropuerto aeropuertoDestino;

    public VueloProgramado() {
    }

    public VueloProgramado(final String idVueloProgramado, final String codigo, final LocalTime horaSalida,
                           final LocalTime horaLlegada, final int capacidadMaxima,
                           final Aeropuerto aeropuertoOrigen, final Aeropuerto aeropuertoDestino) {
        this.idVueloProgramado = idVueloProgramado;
        this.codigo = codigo;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.capacidadMaxima = capacidadMaxima;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
    }

    public String getIdVueloProgramado() {
        return idVueloProgramado;
    }

    public void setIdVueloProgramado(final String idVueloProgramado) {
        this.idVueloProgramado = idVueloProgramado;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(final String codigo) {
        this.codigo = codigo;
    }

    public LocalTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(final LocalTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public LocalTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(final LocalTime horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public int getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(final int capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    public Aeropuerto getAeropuertoOrigen() {
        return aeropuertoOrigen;
    }

    public void setAeropuertoOrigen(final Aeropuerto aeropuertoOrigen) {
        this.aeropuertoOrigen = aeropuertoOrigen;
    }

    public Aeropuerto getAeropuertoDestino() {
        return aeropuertoDestino;
    }

    public void setAeropuertoDestino(final Aeropuerto aeropuertoDestino) {
        this.aeropuertoDestino = aeropuertoDestino;
    }
}
