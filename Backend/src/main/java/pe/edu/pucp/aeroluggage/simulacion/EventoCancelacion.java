package pe.edu.pucp.aeroluggage.simulacion;

import java.time.LocalDateTime;

public class EventoCancelacion {

    private final String idVueloInstancia;
    private final LocalDateTime momentoCancelacion;
    private final String motivo;

    public EventoCancelacion(final String idVueloInstancia, final LocalDateTime momentoCancelacion,
                             final String motivo) {
        this.idVueloInstancia = idVueloInstancia;
        this.momentoCancelacion = momentoCancelacion;
        this.motivo = motivo;
    }

    public EventoCancelacion(final String idVueloInstancia, final LocalDateTime momentoCancelacion) {
        this(idVueloInstancia, momentoCancelacion, null);
    }

    public String getIdVueloInstancia() {
        return idVueloInstancia;
    }

    public LocalDateTime getMomentoCancelacion() {
        return momentoCancelacion;
    }

    public String getMotivo() {
        return motivo;
    }

    @Override
    public String toString() {
        return "EventoCancelacion{vuelo=" + idVueloInstancia
                + ", t=" + momentoCancelacion
                + (motivo != null ? ", motivo=" + motivo : "")
                + '}';
    }
}
