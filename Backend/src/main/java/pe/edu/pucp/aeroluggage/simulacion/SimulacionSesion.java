package pe.edu.pucp.aeroluggage.simulacion;

import lombok.Getter;

import java.time.LocalDate;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class SimulacionSesion {

    private final String sessionId;
    private final LocalDate fechaInicio;
    private final int totalDias;
    private final long intervaloTickMs;
    private final AtomicInteger tickActual = new AtomicInteger(0);
    private final AtomicReference<LocalDate> fechaSimulada;
    private final AtomicBoolean activa = new AtomicBoolean(true);
    private volatile ScheduledFuture<?> tareaScheduled;

    public SimulacionSesion(
            final String sessionId,
            final LocalDate fechaInicio,
            final int totalDias,
            final long intervaloTickMs) {
        this.sessionId = sessionId;
        this.fechaInicio = fechaInicio;
        this.totalDias = totalDias;
        this.intervaloTickMs = intervaloTickMs;
        this.fechaSimulada = new AtomicReference<>(fechaInicio);
    }

    public void setTareaScheduled(final ScheduledFuture<?> tarea) {
        this.tareaScheduled = tarea;
    }

    public void avanzarDia() {
        fechaSimulada.updateAndGet(fecha -> fecha.plusDays(1));
    }

    public boolean haTerminado() {
        return tickActual.get() >= totalDias;
    }
}
