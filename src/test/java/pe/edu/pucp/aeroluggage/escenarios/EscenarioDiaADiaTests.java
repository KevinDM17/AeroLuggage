package pe.edu.pucp.aeroluggage.escenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.algorithms.ga.ConfiguracionGenetico;

class EscenarioDiaADiaTests {
    @Test
    void ejecutor_procesa_lote_diario_ok() {
        final EjecutorEscenario ejecutor = new EjecutorEscenario();
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(8)
            .withGeneracionesMaximas(5)
            .withTiempoMaximoMs(3_000L)
            .withIteracionesBusquedaLocal(2)
            .build();
        final ParametrosEscenario parametros = ParametrosEscenario.diaADia(
            LocalDate.of(2026, 1, 2),
            15,
            configuracion
        );

        final ReporteEscenario reporte = ejecutor.ejecutar(parametros);

        assertEquals(TipoEscenario.DIA_A_DIA, reporte.getTipo());
        assertTrue(reporte.getTotalEnviosProcesados() > 0, "Debe procesar al menos un envio");
        assertTrue(reporte.getTotalMaletasProcesadas() >= reporte.getTotalEnviosProcesados());
        assertNotNull(reporte.getMetricasAgregadas());
        assertTrue(reporte.getTiempoTotalMs() >= 0);
        System.out.println("[Dia a Dia] " + reporte);
    }

    @Test
    void ejecutor_segmenta_envios_en_subloetes_ok() {
        final EjecutorEscenario ejecutor = new EjecutorEscenario();
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(4)
            .withGeneracionesMaximas(3)
            .withTiempoMaximoMs(2_000L)
            .withIteracionesBusquedaLocal(0)
            .build();
        final ParametrosEscenario parametros = ParametrosEscenario.builder()
            .withTipo(TipoEscenario.DIA_A_DIA)
            .withFechaInicio(LocalDate.of(2026, 1, 2))
            .withLimiteEnvios(10)
            .withTamanoLote(5)
            .withDiasSimulacion(1)
            .withConfiguracionGenetico(configuracion)
            .build();

        final ReporteEscenario reporte = ejecutor.ejecutar(parametros);

        assertEquals(2, reporte.getResultadosPorLote().size(), "Debe haber 2 subloetes de 5 envios");
    }
}
