package pe.edu.pucp.aeroluggage.escenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.algorithms.ga.ConfiguracionGenetico;

class EscenarioColapsoTests {
    @Test
    void ejecutor_detecta_colapso_con_lotes_ok() {
        final EjecutorEscenario ejecutor = new EjecutorEscenario();
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(6)
            .withGeneracionesMaximas(4)
            .withTiempoMaximoMs(2_500L)
            .withIteracionesBusquedaLocal(1)
            .build();
        final ParametrosEscenario parametros = ParametrosEscenario.colapso(
            LocalDate.of(2026, 1, 2),
            80,
            0.05,
            configuracion
        );

        final ReporteEscenario reporte = ejecutor.ejecutar(parametros);

        assertEquals(TipoEscenario.COLAPSO_OPERACIONES, reporte.getTipo());
        assertTrue(reporte.getResultadosPorLote().size() >= 1);
        assertNotNull(reporte.getMetricasAgregadas());
        System.out.println("[Colapso] " + reporte);
    }

    @Test
    void ejecutor_calcula_lote_colapso_cuando_umbral_es_bajo_ok() {
        final EjecutorEscenario ejecutor = new EjecutorEscenario();
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(4)
            .withGeneracionesMaximas(3)
            .withTiempoMaximoMs(1_500L)
            .withIteracionesBusquedaLocal(0)
            .build();
        final ParametrosEscenario parametros = ParametrosEscenario.builder()
            .withTipo(TipoEscenario.COLAPSO_OPERACIONES)
            .withFechaInicio(LocalDate.of(2026, 1, 2))
            .withDiasSimulacion(3)
            .withTamanoLote(50)
            .withMaxLotesColapso(5)
            .withUmbralColapso(0.0001)
            .withLimiteEnvios(Integer.MAX_VALUE)
            .withConfiguracionGenetico(configuracion)
            .build();

        final ReporteEscenario reporte = ejecutor.ejecutar(parametros);

        assertTrue(reporte.getResultadosPorLote().size() >= 1);
        assertTrue(reporte.getTotalMaletasProcesadas() > 0);
    }
}
