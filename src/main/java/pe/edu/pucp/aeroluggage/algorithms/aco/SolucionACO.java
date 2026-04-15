package pe.edu.pucp.aeroluggage.algorithms.aco;

import java.util.ArrayList;
import java.util.List;

public class SolucionACO {
    private List<PlanMaletaACO> asignaciones;
    private double tiempoTotal;
    private int incumplimientosDePlazo;
    private int sobrecargaDeVuelos;
    private int sobrecargaDeAlmacenes;
    private int asignacionesNoFactibles;
    private double costoTotal;

    public SolucionACO() {
        this.asignaciones = new ArrayList<>();
    }

    public SolucionACO(final List<PlanMaletaACO> asignaciones, final double tiempoTotal,
        final int incumplimientosDePlazo, final int sobrecargaDeVuelos, final int sobrecargaDeAlmacenes,
        final int asignacionesNoFactibles, final double costoTotal) {
        this.asignaciones = asignaciones;
        this.tiempoTotal = tiempoTotal;
        this.incumplimientosDePlazo = incumplimientosDePlazo;
        this.sobrecargaDeVuelos = sobrecargaDeVuelos;
        this.sobrecargaDeAlmacenes = sobrecargaDeAlmacenes;
        this.asignacionesNoFactibles = asignacionesNoFactibles;
        this.costoTotal = costoTotal;
    }

    public List<PlanMaletaACO> getAsignaciones() {
        return asignaciones;
    }

    public void setAsignaciones(final List<PlanMaletaACO> asignaciones) {
        this.asignaciones = asignaciones;
    }

    public double getTiempoTotal() {
        return tiempoTotal;
    }

    public void setTiempoTotal(final double tiempoTotal) {
        this.tiempoTotal = tiempoTotal;
    }

    public int getIncumplimientosDePlazo() {
        return incumplimientosDePlazo;
    }

    public void setIncumplimientosDePlazo(final int incumplimientosDePlazo) {
        this.incumplimientosDePlazo = incumplimientosDePlazo;
    }

    public int getSobrecargaDeVuelos() {
        return sobrecargaDeVuelos;
    }

    public void setSobrecargaDeVuelos(final int sobrecargaDeVuelos) {
        this.sobrecargaDeVuelos = sobrecargaDeVuelos;
    }

    public int getSobrecargaDeAlmacenes() {
        return sobrecargaDeAlmacenes;
    }

    public void setSobrecargaDeAlmacenes(final int sobrecargaDeAlmacenes) {
        this.sobrecargaDeAlmacenes = sobrecargaDeAlmacenes;
    }

    public int getAsignacionesNoFactibles() {
        return asignacionesNoFactibles;
    }

    public void setAsignacionesNoFactibles(final int asignacionesNoFactibles) {
        this.asignacionesNoFactibles = asignacionesNoFactibles;
    }

    public double getCostoTotal() {
        return costoTotal;
    }

    public void setCostoTotal(final double costoTotal) {
        this.costoTotal = costoTotal;
    }

    @Override
    public String toString() {
        return "SolucionACO{"
            + "asignaciones=" + asignaciones
            + ", tiempoTotal=" + tiempoTotal
            + ", incumplimientosDePlazo=" + incumplimientosDePlazo
            + ", sobrecargaDeVuelos=" + sobrecargaDeVuelos
            + ", sobrecargaDeAlmacenes=" + sobrecargaDeAlmacenes
            + ", asignacionesNoFactibles=" + asignacionesNoFactibles
            + ", costoTotal=" + costoTotal
            + '}';
    }
}
