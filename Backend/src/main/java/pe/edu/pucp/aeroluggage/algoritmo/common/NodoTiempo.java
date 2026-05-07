package pe.edu.pucp.aeroluggage.algoritmo.common;

import java.time.LocalDateTime;
import java.util.Objects;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;

public final class NodoTiempo {

    private final Aeropuerto aeropuerto;
    private final LocalDateTime instante;

    public NodoTiempo(final Aeropuerto aeropuerto, final LocalDateTime instante) {
        this.aeropuerto = aeropuerto;
        this.instante = instante;
    }

    public Aeropuerto getAeropuerto() {
        return aeropuerto;
    }

    public LocalDateTime getInstante() {
        return instante;
    }

    public String getIcao() {
        return aeropuerto != null ? aeropuerto.getIdAeropuerto() : null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodoTiempo)) {
            return false;
        }
        final NodoTiempo otro = (NodoTiempo) o;
        return Objects.equals(getIcao(), otro.getIcao()) && Objects.equals(instante, otro.instante);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIcao(), instante);
    }

    @Override
    public String toString() {
        return "NodoTiempo{" + getIcao() + "@" + instante + '}';
    }
}
