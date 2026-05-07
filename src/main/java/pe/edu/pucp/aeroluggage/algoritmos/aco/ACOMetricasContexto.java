package pe.edu.pucp.aeroluggage.algoritmos.aco;

final class ACOMetricasContexto {
    private static final ThreadLocal<ACOMetricas> ACTUAL = new ThreadLocal<>();

    private ACOMetricasContexto() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static void fijar(final ACOMetricas metricas) {
        ACTUAL.set(metricas);
    }

    static ACOMetricas obtener() {
        return ACTUAL.get();
    }

    static void limpiar() {
        ACTUAL.remove();
    }
}
