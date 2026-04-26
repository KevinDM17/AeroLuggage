package pe.edu.pucp.aeroluggage.simulacion;

public final class SimulacionTemporalApplication {

    private static final String MODO_GA = "ga";
    private static final String MODO_ACO = "aco";
    private static final String MODO_TODOS = "todos";

    private SimulacionTemporalApplication() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void main(final String[] args) {
        final String modo = obtenerModo(args);
        if (MODO_GA.equals(modo)) {
            SimulacionTemporalRunner.ejecutarGa();
            return;
        }
        if (MODO_ACO.equals(modo)) {
            SimulacionTemporalRunner.ejecutarAco();
            return;
        }
        SimulacionTemporalRunner.ejecutarDesdeConfiguracion();
    }

    private static String obtenerModo(final String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            return MODO_TODOS;
        }
        return args[0].trim().toLowerCase();
    }
}
