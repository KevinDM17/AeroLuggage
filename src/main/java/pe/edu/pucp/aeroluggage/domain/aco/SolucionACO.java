package pe.edu.pucp.aeroluggage.domain.aco;

import java.util.ArrayList;
import java.util.List;

public class SolucionACO {
    private List<PlanMaletaACO> planesMaleta;
    private double tiempoTotal;
    private int incumplimientosDePlazo;
    private int sobrecargaDeVuelos;
    private int sobrecargaDeAlmacenes;
    private int numeroDeReplanificaciones;
    private double costoTotal;

    public SolucionACO() {
        this.planesMaleta = new ArrayList<>();
    }

    public SolucionACO(final List<PlanMaletaACO> planesMaleta, final double tiempoTotal,
        final int incumplimientosDePlazo, final int sobrecargaDeVuelos, final int sobrecargaDeAlmacenes,
        final int numeroDeReplanificaciones, final double costoTotal) {
        this.planesMaleta = planesMaleta;
        this.tiempoTotal = tiempoTotal;
        this.incumplimientosDePlazo = incumplimientosDePlazo;
        this.sobrecargaDeVuelos = sobrecargaDeVuelos;
        this.sobrecargaDeAlmacenes = sobrecargaDeAlmacenes;
        this.numeroDeReplanificaciones = numeroDeReplanificaciones;
        this.costoTotal = costoTotal;
    }

    public List<PlanMaletaACO> getPlanesMaleta() {
        return planesMaleta;
    }

    public void setPlanesMaleta(final List<PlanMaletaACO> planesMaleta) {
        this.planesMaleta = planesMaleta;
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

    public int getNumeroDeReplanificaciones() {
        return numeroDeReplanificaciones;
    }

    public void setNumeroDeReplanificaciones(final int numeroDeReplanificaciones) {
        this.numeroDeReplanificaciones = numeroDeReplanificaciones;
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
            + "planesMaleta=" + planesMaleta
            + ", tiempoTotal=" + tiempoTotal
            + ", incumplimientosDePlazo=" + incumplimientosDePlazo
            + ", sobrecargaDeVuelos=" + sobrecargaDeVuelos
            + ", sobrecargaDeAlmacenes=" + sobrecargaDeAlmacenes
            + ", numeroDeReplanificaciones=" + numeroDeReplanificaciones
            + ", costoTotal=" + costoTotal
            + '}';
    }
}
