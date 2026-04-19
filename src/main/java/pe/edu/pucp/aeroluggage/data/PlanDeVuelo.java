package pe.edu.pucp.aeroluggage.data;

import java.time.LocalTime;

public final class PlanDeVuelo {
    private final String origen;
    private final String destino;
    private final LocalTime horaSalida;
    private final LocalTime horaLlegada;
    private final int capacidad;

    public PlanDeVuelo(final String origen, final String destino, final LocalTime horaSalida,
        final LocalTime horaLlegada, final int capacidad) {
        this.origen = origen;
        this.destino = destino;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.capacidad = capacidad;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public LocalTime getHoraSalida() {
        return horaSalida;
    }

    public LocalTime getHoraLlegada() {
        return horaLlegada;
    }

    public int getCapacidad() {
        return capacidad;
    }
}
