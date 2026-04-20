package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.LocalDateTime;

import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;

public class Pedido {
    private String idPedido;
    private Aeropuerto aeropuertoOrigen;
    private Aeropuerto aeropuertoDestino;
    private LocalDateTime fechaRegistro;
    private int cantidadMaletas;
    private EstadoPedido estado;

    public Pedido() {
    }

    public Pedido(final String idPedido, final Aeropuerto aeropuertoOrigen, final Aeropuerto aeropuertoDestino,
                  final LocalDateTime fechaRegistro, final int cantidadMaletas, final EstadoPedido estado) {
        this.idPedido = idPedido;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.fechaRegistro = fechaRegistro;
        this.cantidadMaletas = cantidadMaletas;
        this.estado = estado;
    }

    public String getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(final String idPedido) {
        this.idPedido = idPedido;
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

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(final LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public int getCantidadMaletas() {
        return cantidadMaletas;
    }

    public void setCantidadMaletas(final int cantidadMaletas) {
        this.cantidadMaletas = cantidadMaletas;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(final EstadoPedido estado) {
        this.estado = estado;
    }
}