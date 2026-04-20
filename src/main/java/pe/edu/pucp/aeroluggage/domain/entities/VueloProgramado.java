package pe.edu.pucp.aeroluggage.domain.entities;

import java.time.LocalDateTime;

public class VueloProgramado {
    private String idVueloProgramado;
    private String codigo;
    private LocalDateTime horaSalida;
    private LocalDateTime horaLlegada;
    private int capacidadMaxima;
    private Aeropuerto aeropuertoOrigen;
    private Aeropuerto aeropuertoDestino;

    public VueloProgramado() {
    }

    public VueloProgramado(final String idVueloProgramado, final String codigo, final LocalDateTime horaSalida,
                           final LocalDateTime horaLlegada, final int capacidadMaxima,
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

    public LocalDateTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(final LocalDateTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public LocalDateTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(final LocalDateTime horaLlegada) {
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