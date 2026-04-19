package pe.edu.pucp.aeroluggage.escenarios;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import pe.edu.pucp.aeroluggage.algorithms.ga.ConfiguracionGenetico;
import pe.edu.pucp.aeroluggage.algorithms.ga.ResultadoGenetico;

public final class EjecutorEscenarioMain {
    private static final LocalDate FECHA_INICIO = LocalDate.of(2026, 1, 2);

    private EjecutorEscenarioMain() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void main(final String[] args) {
        final TipoEscenario tipo = resolverEscenario(args);
        final EjecutorEscenario ejecutor = new EjecutorEscenario();
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(30)
            .withGeneracionesMaximas(40)
            .withTiempoMaximoMs(30_000L)
            .withIteracionesBusquedaLocal(5)
            .build();
        final ParametrosEscenario parametros = construirParametros(tipo, configuracion);
        System.out.println("=== Ejecutando escenario: " + tipo + " ===");
        final ReporteEscenario reporte = ejecutor.ejecutar(parametros);
        imprimirReporte(reporte);
    }

    private static TipoEscenario resolverEscenario(final String[] args) {
        if (args == null || args.length == 0) {
            return TipoEscenario.DIA_A_DIA;
        }
        final String valor = args[0].trim().toUpperCase();
        return Arrays.stream(TipoEscenario.values())
            .filter(tipo -> tipo.name().equals(valor))
            .findFirst()
            .orElse(TipoEscenario.DIA_A_DIA);
    }

    private static ParametrosEscenario construirParametros(final TipoEscenario tipo,
        final ConfiguracionGenetico configuracion) {
        switch (tipo) {
            case DIA_A_DIA:
                return ParametrosEscenario.diaADia(FECHA_INICIO, 50, configuracion);
            case SIMULACION_PERIODO:
                return ParametrosEscenario.simulacionPeriodo(FECHA_INICIO, 3, 300, configuracion);
            case COLAPSO_OPERACIONES:
                return ParametrosEscenario.colapso(FECHA_INICIO, 150, 0.15, configuracion);
            default:
                return ParametrosEscenario.diaADia(FECHA_INICIO, 50, configuracion);
        }
    }

    private static void imprimirReporte(final ReporteEscenario reporte) {
        System.out.println(reporte);
        final List<ResultadoGenetico> resultados = reporte.getResultadosPorLote();
        for (int i = 0; i < resultados.size(); i++) {
            final ResultadoGenetico resultado = resultados.get(i);
            System.out.println("Lote " + (i + 1) + " -> " + resultado);
        }
        System.out.println("Color semaforo agregado: "
            + (reporte.getMetricasAgregadas() == null ? "N/A" : reporte.getMetricasAgregadas().colorSemaforo()));
    }
}
