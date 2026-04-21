package pe.edu.pucp.aeroluggage.algoritmos.aco;

public class ACOReporte {
    private int intervalosProcesados;
    private int planesConfirmados;
    private int rutasFactibles;
    private int rutasNoFactibles;
    private double tiempoTotalDias;
    private int incumplimientosPlazo;
    private int sobrecargaVuelos;
    private int sobrecargaAlmacenes;
    private int numeroReplanificaciones;
    private double mejorCosto;

    public int getIntervalosProcesados() {
        return intervalosProcesados;
    }

    public void setIntervalosProcesados(final int intervalosProcesados) {
        this.intervalosProcesados = intervalosProcesados;
    }

    public int getPlanesConfirmados() {
        return planesConfirmados;
    }

    public void setPlanesConfirmados(final int planesConfirmados) {
        this.planesConfirmados = planesConfirmados;
    }

    public int getRutasFactibles() {
        return rutasFactibles;
    }

    public void setRutasFactibles(final int rutasFactibles) {
        this.rutasFactibles = rutasFactibles;
    }

    public int getRutasNoFactibles() {
        return rutasNoFactibles;
    }

    public void setRutasNoFactibles(final int rutasNoFactibles) {
        this.rutasNoFactibles = rutasNoFactibles;
    }

    public double getTiempoTotalDias() {
        return tiempoTotalDias;
    }

    public void setTiempoTotalDias(final double tiempoTotalDias) {
        this.tiempoTotalDias = tiempoTotalDias;
    }

    public int getIncumplimientosPlazo() {
        return incumplimientosPlazo;
    }

    public void setIncumplimientosPlazo(final int incumplimientosPlazo) {
        this.incumplimientosPlazo = incumplimientosPlazo;
    }

    public int getSobrecargaVuelos() {
        return sobrecargaVuelos;
    }

    public void setSobrecargaVuelos(final int sobrecargaVuelos) {
        this.sobrecargaVuelos = sobrecargaVuelos;
    }

    public int getSobrecargaAlmacenes() {
        return sobrecargaAlmacenes;
    }

    public void setSobrecargaAlmacenes(final int sobrecargaAlmacenes) {
        this.sobrecargaAlmacenes = sobrecargaAlmacenes;
    }

    public int getNumeroReplanificaciones() {
        return numeroReplanificaciones;
    }

    public void setNumeroReplanificaciones(final int numeroReplanificaciones) {
        this.numeroReplanificaciones = numeroReplanificaciones;
    }

    public double getMejorCosto() {
        return mejorCosto;
    }

    public void setMejorCosto(final double mejorCosto) {
        this.mejorCosto = mejorCosto;
    }
}
