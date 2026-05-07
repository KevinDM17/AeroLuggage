package pe.edu.pucp.aeroluggage.algoritmo.aco;

final class EvaluacionACO {
    private final double costo;
    private final double tiempoTotalDias;
    private final int incumplimientosPlazo;
    private final int rutasFactibles;
    private final int rutasNoFactibles;
    private final int sobrecargaVuelos;
    private final double sobrecargaAlmacenes;
    private final int numeroReplanificaciones;

    EvaluacionACO(
            final double costo,
            final double tiempoTotalDias,
            final int incumplimientosPlazo,
            final int rutasFactibles,
            final int rutasNoFactibles,
            final int sobrecargaVuelos,
            final double sobrecargaAlmacenes,
            final int numeroReplanificaciones
    ) {
        this.costo = costo;
        this.tiempoTotalDias = tiempoTotalDias;
        this.incumplimientosPlazo = incumplimientosPlazo;
        this.rutasFactibles = rutasFactibles;
        this.rutasNoFactibles = rutasNoFactibles;
        this.sobrecargaVuelos = sobrecargaVuelos;
        this.sobrecargaAlmacenes = sobrecargaAlmacenes;
        this.numeroReplanificaciones = numeroReplanificaciones;
    }

    double getCosto() {
        return costo;
    }

    double getTiempoTotalDias() {
        return tiempoTotalDias;
    }

    int getIncumplimientosPlazo() {
        return incumplimientosPlazo;
    }

    int getRutasFactibles() {
        return rutasFactibles;
    }

    int getRutasNoFactibles() {
        return rutasNoFactibles;
    }

    int getSobrecargaVuelos() {
        return sobrecargaVuelos;
    }

    double getSobrecargaAlmacenes() {
        return sobrecargaAlmacenes;
    }

    int getNumeroReplanificaciones() {
        return numeroReplanificaciones;
    }
}
