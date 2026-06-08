package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.LocalDateTime;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;

public class Maleta {
    private String idMaleta;
    private Pedido pedido;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaLlegada;
    private EstadoMaleta estado;
    private String aeropuertoActual;

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

    public Maleta(final String idMaleta, final Pedido pedido, final LocalDateTime fechaRegistro,
                  final LocalDateTime fechaLlegada, final String estado) {
        this(idMaleta, pedido, fechaRegistro, fechaLlegada, convertirEstado(estado));
    }

    public Maleta(final Maleta other, final Map<String, Aeropuerto> aeropuertoIndex) {
        this.idMaleta = other.idMaleta;
        this.pedido = other.pedido != null ? new Pedido(other.pedido, aeropuertoIndex) : null;
        this.fechaRegistro = other.fechaRegistro;
        this.fechaLlegada = other.fechaLlegada;
        this.estado = other.estado;
        this.aeropuertoActual = other.aeropuertoActual;
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

    public void setEstado(final String estado) {
        this.estado = convertirEstado(estado);
    }

    public String getAeropuertoActual() {
        if (estado == EstadoMaleta.ENTREGADA) {
            return pedido != null && pedido.getAeropuertoDestino() != null
                    ? pedido.getAeropuertoDestino().getIdAeropuerto()
                    : null;
        }
        if (estado == EstadoMaleta.EN_TRANSITO) {
            return null;
        }
        return aeropuertoActual;
    }

    public void setAeropuertoActual(final String aeropuertoActual) {
        this.aeropuertoActual = aeropuertoActual;
    }

    private static EstadoMaleta convertirEstado(final String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        final String normalizado = estado.trim();
        if ("PENDIENTE".equals(normalizado) || "EN_RUTA".equals(normalizado)) {
            return EstadoMaleta.EN_ALMACEN;
        }
        try {
            return EstadoMaleta.valueOf(normalizado);
        } catch (final IllegalArgumentException exception) {
            return EstadoMaleta.EN_ALMACEN;
        }
    }
}
