package pe.edu.pucp.aeroluggage.simulacion;

public final class SimulacionTemporalApplication {

    private static final String MODO_GA = "ga";
    private static final String MODO_ACO = "aco";
    private static final String MODO_EXPERIMENTO = "experimento";
    private static final String MODO_DETALLADO = "detallado";
    private static final String MODO_EXPERIMENTO_LIMITES = "experimentolimites";
    private static final String MODO_PROMEDIO_DURACION = "promedioduracion";
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
        if (MODO_EXPERIMENTO.equals(modo)) {
            SimulacionTemporalRunner.ejecutarExperimento();
            return;
        }
        if (MODO_DETALLADO.equals(modo)) {
            SimulacionTemporalRunner.ejecutarDetallado();
            return;
        }
        if (MODO_EXPERIMENTO_LIMITES.equals(modo)) {
            ExperimentoLimitesRunner.ejecutar();
            return;
        }
        if (MODO_PROMEDIO_DURACION.equals(modo)) {
            PromedioDuracionRunner.ejecutar();
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
