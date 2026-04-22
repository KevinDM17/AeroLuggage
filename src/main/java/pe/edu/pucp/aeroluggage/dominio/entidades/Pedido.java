package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.LocalDateTime;

import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;

public class Pedido {
    private static final long PLAZO_MISMO_CONTINENTE_DIAS = 1L;
    private static final long PLAZO_INTERCONTINENTAL_DIAS = 2L;

    private String idPedido;
    private Aeropuerto aeropuertoOrigen;
    private Aeropuerto aeropuertoDestino;
    private LocalDateTime fechaHoraPlazo;
    private LocalDateTime fechaRegistro;
    private int cantidadMaletas;
    private EstadoPedido estado;

    public Pedido() {
    }

    public Pedido(final String idPedido, final Aeropuerto aeropuertoOrigen, final Aeropuerto aeropuertoDestino,
                  final LocalDateTime fechaRegistro, final int cantidadMaletas, final String estado) {
        this(
                idPedido,
                aeropuertoOrigen,
                aeropuertoDestino,
                null,
                fechaRegistro,
                cantidadMaletas,
                convertirEstado(estado)
        );
    }

    public Pedido(final String idPedido, final Aeropuerto aeropuertoOrigen, final Aeropuerto aeropuertoDestino,
                  final LocalDateTime fechaHoraPlazo, final LocalDateTime fechaRegistro,
                  final int cantidadMaletas, final String estado) {
        this.idPedido = idPedido;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.fechaHoraPlazo = fechaHoraPlazo;
        this.fechaRegistro = fechaRegistro;
        this.cantidadMaletas = cantidadMaletas;
        this.estado = convertirEstado(estado);
    }

    public Pedido(final String idPedido, final Aeropuerto aeropuertoOrigen, final Aeropuerto aeropuertoDestino,
                  final LocalDateTime fechaRegistro, final LocalDateTime fechaHoraPlazo,
                  final int cantidadMaletas, final EstadoPedido estado) {
        this.idPedido = idPedido;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.fechaHoraPlazo = fechaHoraPlazo;
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

    public LocalDateTime getFechaHoraPlazo() {
        return fechaHoraPlazo;
    }

    public void setFechaHoraPlazo(final LocalDateTime fechaHoraPlazo) {
        this.fechaHoraPlazo = fechaHoraPlazo;
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

    public void setEstado(final String estado) {
        this.estado = convertirEstado(estado);
    }

    public void registrarPedido() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoPedido.REGISTRADO;
        }
        fechaHoraPlazo = calcularFechaHoraPlazo();
    }

    public LocalDateTime calcularFechaHoraPlazo() {
        if (fechaRegistro == null) {
            return null;
        }
        final Continente continenteOrigen = obtenerContinente(aeropuertoOrigen);
        final Continente continenteDestino = obtenerContinente(aeropuertoDestino);
        if (continenteOrigen == null || continenteDestino == null) {
            return null;
        }
        final long plazoDias = continenteOrigen == continenteDestino
                ? PLAZO_MISMO_CONTINENTE_DIAS
                : PLAZO_INTERCONTINENTAL_DIAS;
        fechaHoraPlazo = fechaRegistro.plusDays(plazoDias);
        return fechaHoraPlazo;
    }

    private Continente obtenerContinente(final Aeropuerto aeropuerto) {
        if (aeropuerto == null || aeropuerto.getCiudad() == null) {
            return null;
        }
        return aeropuerto.getCiudad().getContinente();
    }

    private static EstadoPedido convertirEstado(final String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        try {
            return EstadoPedido.valueOf(estado.trim());
        } catch (final IllegalArgumentException exception) {
            return EstadoPedido.REGISTRADO;
        }
    }
}
