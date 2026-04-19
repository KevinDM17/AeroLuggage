package pe.edu.pucp.aeroluggage.ga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.algorithms.ga.AlgoritmoGeneticoHibrido;
import pe.edu.pucp.aeroluggage.algorithms.ga.ConfiguracionGenetico;
import pe.edu.pucp.aeroluggage.algorithms.ga.ResultadoGenetico;
import pe.edu.pucp.aeroluggage.data.CargadorDatos;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

class AlgoritmoGeneticoHibridoTests {
    @Test
    void algoritmo_asigna_maleta_a_vuelo_directo_ok() {
        final List<MaletaProcesada> maletas = new ArrayList<>();
        final List<Vuelo> vuelos = new ArrayList<>();
        final List<Aeropuerto> aeropuertos = new ArrayList<>();

        maletas.add(new MaletaProcesada("MAL-001", "ENV-001", "EBCI", "LATI", 1));
        vuelos.add(new Vuelo(
            "VUE-0000001",
            "EBCI-LATI-2026-01-02-08:00",
            fecha("2026-01-02T08:00:00Z"),
            fecha("2026-01-02T12:00:00Z"),
            200,
            200,
            "PROGRAMADO"
        ));
        aeropuertos.add(new Aeropuerto("EBCI", "CIU-001", "Bruselas", 600, 0, 4.45f, 50.45f));
        aeropuertos.add(new Aeropuerto("LATI", "CIU-002", "Tirana", 600, 0, 19.72f, 41.41f));

        final InstanciaProblema instancia = new InstanciaProblema(maletas, vuelos, aeropuertos);
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(6)
            .withGeneracionesMaximas(5)
            .withTiempoMaximoMs(2_000L)
            .withIteracionesBusquedaLocal(2)
            .build();
        final AlgoritmoGeneticoHibrido algoritmo = new AlgoritmoGeneticoHibrido(instancia, configuracion);

        final ResultadoGenetico resultado = algoritmo.ejecutar();

        assertNotNull(resultado.getMejorIndividuo());
        assertEquals(1, resultado.getMetricas().getTotalMaletas());
        assertEquals(1, resultado.getMetricas().getMaletasAsignadas());
        assertEquals(0, resultado.getMetricas().getMaletasSinRuta());
        assertTrue(resultado.esSolucionFactible());
    }

    @Test
    void algoritmo_penaliza_maleta_sin_ruta_ok() {
        final List<MaletaProcesada> maletas = new ArrayList<>();
        final List<Vuelo> vuelos = new ArrayList<>();
        final List<Aeropuerto> aeropuertos = new ArrayList<>();

        maletas.add(new MaletaProcesada("MAL-001", "ENV-001", "EBCI", "OYSN", 2));
        aeropuertos.add(new Aeropuerto("EBCI", "CIU-001", "Bruselas", 600, 0, 4.45f, 50.45f));
        aeropuertos.add(new Aeropuerto("OYSN", "CIU-002", "Sana", 600, 0, 44.21f, 15.47f));

        final InstanciaProblema instancia = new InstanciaProblema(maletas, vuelos, aeropuertos);
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(4)
            .withGeneracionesMaximas(3)
            .withTiempoMaximoMs(1_000L)
            .withIteracionesBusquedaLocal(0)
            .build();
        final AlgoritmoGeneticoHibrido algoritmo = new AlgoritmoGeneticoHibrido(instancia, configuracion);

        final ResultadoGenetico resultado = algoritmo.ejecutar();

        assertEquals(1, resultado.getMetricas().getMaletasSinRuta());
        assertFalse(resultado.esSolucionFactible());
        assertTrue(resultado.getMetricas().getPenalizacionTotal() > 0);
    }

    @Test
    void algoritmo_corre_sobre_dataset_real_ok() {
        final InstanciaProblema instancia = CargadorDatos.cargarInstanciaCompleta(
            LocalDate.of(2026, 1, 2),
            2,
            25
        );
        final ConfiguracionGenetico configuracion = ConfiguracionGenetico.builder()
            .withTamanoPoblacion(12)
            .withGeneracionesMaximas(8)
            .withTiempoMaximoMs(5_000L)
            .withIteracionesBusquedaLocal(3)
            .build();
        final AlgoritmoGeneticoHibrido algoritmo = new AlgoritmoGeneticoHibrido(instancia, configuracion);

        final ResultadoGenetico resultado = algoritmo.ejecutar();

        assertNotNull(resultado);
        assertTrue(resultado.getGeneracionesEjecutadas() >= 1);
        assertTrue(resultado.getMetricas().getTotalMaletas() > 0);
        assertNotNull(resultado.getMetricas().colorSemaforo());
    }

    private static Date fecha(final String iso) {
        return Date.from(java.time.Instant.parse(iso));
    }
}
