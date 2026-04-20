package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.LocalDateTime;

import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public class VueloInstancia {
    private String idVueloInstancia;
    private String codigo;
    private LocalDateTime fechaSalida;
    private LocalDateTime fechaLlegada;
    private int capacidadMaxima;
    private int capacidadDisponible;
    private Aeropuerto aeropuertoOrigen;
    private Aeropuerto aeropuertoDestino;
    private EstadoVuelo estado;

    public VueloInstancia() {
    }

    public VueloInstancia(final String idVueloInstancia, final String codigo, final LocalDateTime fechaSalida,
                            final LocalDateTime fechaLlegada, final int capacidadMaxima,
                            final int capacidadDisponible, final Aeropuerto aeropuertoOrigen,
                            final Aeropuerto aeropuertoDestino, final EstadoVuelo estado) {
        this.idVueloInstancia = idVueloInstancia;
        this.codigo = codigo;
        this.fechaSalida = fechaSalida;
        this.fechaLlegada = fechaLlegada;
        this.capacidadMaxima = capacidadMaxima;
        this.capacidadDisponible = capacidadDisponible;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.estado = estado;
    }

    public String getIdVueloInstancia() {
        return idVueloInstancia;
    }

    public void setIdVueloInstancia(final String idVueloInstancia) {
        this.idVueloInstancia = idVueloInstancia;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(final String codigo) {
        this.codigo = codigo;
    }

    public LocalDateTime getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(final LocalDateTime fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public LocalDateTime getFechaLlegada() {
        return fechaLlegada;
    }

    public void setFechaLlegada(final LocalDateTime fechaLlegada) {
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

    public EstadoVuelo getEstado() {
        return estado;
    }

    public void setEstado(final EstadoVuelo estado) {
        this.estado = estado;
    }

    public void cancelar() {
        this.estado = EstadoVuelo.CANCELADO;
        this.capacidadDisponible = this.capacidadMaxima;
    }

    public void actualizarCapacidad(final int delta) {
        final int nuevaCapacidad = this.capacidadDisponible - delta;
        if (nuevaCapacidad < 0) {
            throw new IllegalStateException(
                    "Capacidad disponible insuficiente en vuelo " + this.idVueloInstancia
                            + ": disponible=" + this.capacidadDisponible + ", delta=" + delta);
        }
        if (nuevaCapacidad > this.capacidadMaxima) {
            throw new IllegalStateException(
                    "Capacidad disponible excede maxima en vuelo " + this.idVueloInstancia
                            + ": max=" + this.capacidadMaxima + ", calculada=" + nuevaCapacidad);
        }
        this.capacidadDisponible = nuevaCapacidad;
    }
}