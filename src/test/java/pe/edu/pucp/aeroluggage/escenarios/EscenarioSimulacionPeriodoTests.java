package pe.edu.pucp.aeroluggage.escenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.algorithms.ga.ConfiguracionGenetico;
import pe.edu.pucp.aeroluggage.algorithms.ga.MetricasSolucion;

class EscenarioSimulacionPeriodoTests {
    @Test
    void ejecutor_procesa_simulacion_de_3_dias_ok() {
        final EjecutorEscenario ejecutor = new EjecutorEscenario();
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(15)
            .withGeneracionesMaximas(12)
            .withTiempoMaximoMs(10_000L)
            .withIteracionesBusquedaLocal(4)
            .build();
        final ParametrosEscenario parametros = ParametrosEscenario.simulacionPeriodo(
            LocalDate.of(2026, 1, 2),
            3,
            120,
            configuracion
        );

        final ReporteEscenario reporte = ejecutor.ejecutar(parametros);

        assertEquals(TipoEscenario.SIMULACION_PERIODO, reporte.getTipo());
        assertEquals(1, reporte.getResultadosPorLote().size(), "La simulacion de periodo ejecuta una sola corrida");
        assertTrue(reporte.getTotalMaletasProcesadas() > 0);
        final MetricasSolucion metricas = reporte.getMetricasAgregadas();
        assertNotNull(metricas);
        assertTrue(metricas.getTotalMaletas() > 0);
        System.out.println("[Simulacion Periodo] " + reporte);
    }

    @Test
    void ejecutor_calcula_color_semaforo_ok() {
        final EjecutorEscenario ejecutor = new EjecutorEscenario();
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(6)
            .withGeneracionesMaximas(4)
            .withTiempoMaximoMs(3_000L)
            .withIteracionesBusquedaLocal(1)
            .build();
        final ParametrosEscenario parametros = ParametrosEscenario.simulacionPeriodo(
            LocalDate.of(2026, 1, 2),
            1,
            30,
            configuracion
        );

        final ReporteEscenario reporte = ejecutor.ejecutar(parametros);

        final String color = reporte.getMetricasAgregadas().colorSemaforo();
        assertTrue("VERDE".equals(color) || "AMBAR".equals(color) || "ROJO".equals(color),
            "El color semaforo debe ser VERDE/AMBAR/ROJO pero fue " + color);
    }
}
