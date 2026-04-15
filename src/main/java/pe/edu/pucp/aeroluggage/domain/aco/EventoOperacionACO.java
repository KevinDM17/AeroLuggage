package pe.edu.pucp.aeroluggage.domain.aco;

import java.util.Date;

public class EventoOperacionACO {
    private String idEvento;
    private String tipoEvento;
    private String entidadAfectada;
    private String idEntidadAfectada;
    private Date fechaEvento;
    private String descripcion;

    public EventoOperacionACO() {
    }

    public EventoOperacionACO(final String idEvento, final String tipoEvento, final String entidadAfectada,
        final String idEntidadAfectada, final Date fechaEvento, final String descripcion) {
        this.idEvento = idEvento;
        this.tipoEvento = tipoEvento;
        this.entidadAfectada = entidadAfectada;
        this.idEntidadAfectada = idEntidadAfectada;
        this.fechaEvento = fechaEvento;
        this.descripcion = descripcion;
    }

    public String getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(final String idEvento) {
        this.idEvento = idEvento;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(final String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getEntidadAfectada() {
        return entidadAfectada;
    }

    public void setEntidadAfectada(final String entidadAfectada) {
        this.entidadAfectada = entidadAfectada;
    }

    public String getIdEntidadAfectada() {
        return idEntidadAfectada;
    }

    public void setIdEntidadAfectada(final String idEntidadAfectada) {
        this.idEntidadAfectada = idEntidadAfectada;
    }

    public Date getFechaEvento() {
        return fechaEvento;
    }

    public void setFechaEvento(final Date fechaEvento) {
        this.fechaEvento = fechaEvento;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(final String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String toString() {
        return "EventoOperacionACO{"
            + "idEvento='" + idEvento + '\''
            + ", tipoEvento='" + tipoEvento + '\''
            + ", entidadAfectada='" + entidadAfectada + '\''
            + ", idEntidadAfectada='" + idEntidadAfectada + '\''
            + ", fechaEvento=" + fechaEvento
            + ", descripcion='" + descripcion + '\''
            + '}';
    }
}
