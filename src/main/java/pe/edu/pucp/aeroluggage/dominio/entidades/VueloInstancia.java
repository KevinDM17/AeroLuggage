package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.LocalDate;
import java.time.LocalDateTime;

import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public class VueloInstancia {
    private String idVueloInstancia;
    private String codigo;
    private VueloProgramado vueloProgramado;
    private LocalDate fechaOperacion;
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
        this(
                idVueloInstancia,
                codigo,
                null,
                fechaSalida == null ? null : fechaSalida.toLocalDate(),
                fechaSalida,
                fechaLlegada,
                capacidadMaxima,
                capacidadDisponible,
                aeropuertoOrigen,
                aeropuertoDestino,
                estado
        );
    }

    public VueloInstancia(final String idVueloInstancia, final VueloProgramado vueloProgramado,
                          final LocalDate fechaOperacion, final LocalDateTime fechaSalida,
                          final LocalDateTime fechaLlegada, final int capacidadTotal,
                          final int capacidadDisponible, final EstadoVuelo estado) {
        this(
                idVueloInstancia,
                vueloProgramado == null ? null : vueloProgramado.getCodigo(),
                vueloProgramado,
                fechaOperacion,
                fechaSalida,
                fechaLlegada,
                capacidadTotal,
                capacidadDisponible,
                vueloProgramado == null ? null : vueloProgramado.getAeropuertoOrigen(),
                vueloProgramado == null ? null : vueloProgramado.getAeropuertoDestino(),
                estado
        );
    }

    public VueloInstancia(final String idVueloInstancia, final String codigo, final VueloProgramado vueloProgramado,
                          final LocalDate fechaOperacion, final LocalDateTime fechaSalida,
                          final LocalDateTime fechaLlegada, final int capacidadMaxima,
                          final int capacidadDisponible, final Aeropuerto aeropuertoOrigen,
                          final Aeropuerto aeropuertoDestino, final EstadoVuelo estado) {
        this.idVueloInstancia = idVueloInstancia;
        this.codigo = codigo;
        this.vueloProgramado = vueloProgramado;
        this.fechaOperacion = fechaOperacion;
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

    public VueloProgramado getVueloProgramado() {
        return vueloProgramado;
    }

    public void setVueloProgramado(final VueloProgramado vueloProgramado) {
        this.vueloProgramado = vueloProgramado;
    }

    public LocalDate getFechaOperacion() {
        return fechaOperacion;
    }

    public void setFechaOperacion(final LocalDate fechaOperacion) {
        this.fechaOperacion = fechaOperacion;
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
        estado = EstadoVuelo.CANCELADO;
        capacidadDisponible = 0;
    }

    public void actualizarCapacidad() {
        if (capacidadMaxima < 0) {
            capacidadMaxima = 0;
        }
        if (estado == EstadoVuelo.CANCELADO) {
            capacidadDisponible = 0;
            return;
        }
        if (capacidadDisponible < 0) {
            capacidadDisponible = 0;
            return;
        }
        if (capacidadDisponible > capacidadMaxima) {
            capacidadDisponible = capacidadMaxima;
        }
    }

    public void actualizarCapacidad(final int delta) {
        final int nuevaCapacidad = capacidadDisponible - delta;
        if (nuevaCapacidad < 0) {
            throw new IllegalStateException(
                    "Capacidad disponible insuficiente en vuelo " + idVueloInstancia
                            + ": disponible=" + capacidadDisponible + ", delta=" + delta
            );
        }
        if (nuevaCapacidad > capacidadMaxima) {
            throw new IllegalStateException(
                    "Capacidad disponible excede maxima en vuelo " + idVueloInstancia
                            + ": max=" + capacidadMaxima + ", calculada=" + nuevaCapacidad
            );
        }
        capacidadDisponible = nuevaCapacidad;
    }
}
