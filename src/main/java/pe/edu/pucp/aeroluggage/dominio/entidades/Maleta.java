package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.LocalDateTime;

import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;

public class Maleta {
    private String idMaleta;
    private Pedido pedido;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaLlegada;
    private EstadoMaleta estado;

    public Maleta() {
    }

    public Maleta(final String idMaleta, final Pedido pedido, final LocalDateTime fechaRegistro,
                  final LocalDateTime fechaLlegada, final EstadoMaleta estado) {
        this.idMaleta = idMaleta;
        this.pedido = pedido;
        this.fechaRegistro = fechaRegistro;
        this.fechaLlegada = fechaLlegada;
        this.estado = estado;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(final Pedido pedido) {
        this.pedido = pedido;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(final LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public LocalDateTime getFechaLlegada() {
        return fechaLlegada;
    }

    public void setFechaLlegada(final LocalDateTime fechaLlegada) {
        this.fechaLlegada = fechaLlegada;
    }

    public EstadoMaleta getEstado() {
        return estado;
    }

    public void setEstado(final EstadoMaleta estado) {
        this.estado = estado;
    }
}