package pe.edu.pucp.aeroluggage.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Envio;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

class CargadorDatosTests {
    @Test
    void aeropuerto_loader_lee_archivo_utf16_ok() {
        final List<Aeropuerto> aeropuertos = AeropuertoLoader.cargarDesdeClasspath(CargadorDatos.RECURSO_AEROPUERTOS);

        assertFalse(aeropuertos.isEmpty(), "Debe cargar al menos un aeropuerto");
        assertTrue(aeropuertos.size() >= 20, "Debe haber al menos 20 aeropuertos");
        final Aeropuerto primero = aeropuertos.get(0);
        assertEquals("SKBO", primero.getIdAeropuerto());
        assertTrue(primero.getCapacidadAlmacen() > 0);
    }

    @Test
    void aeropuerto_loader_extrae_husos_horarios_ok() {
        final Map<String, Integer> husos = AeropuertoLoader.husosHorarios(CargadorDatos.RECURSO_AEROPUERTOS);

        assertFalse(husos.isEmpty());
        assertEquals(Integer.valueOf(-5), husos.get("SKBO"));
        assertEquals(Integer.valueOf(-3), husos.get("SCEL"));
        assertEquals(Integer.valueOf(2), husos.get("EBCI"));
    }

    @Test
    void vuelo_loader_parsea_planes_y_expande_vuelos_ok() {
        final List<PlanDeVuelo> planes = VueloLoader.cargarPlanes(CargadorDatos.RECURSO_PLANES_VUELO);
        final Map<String, Integer> husos = AeropuertoLoader.husosHorarios(CargadorDatos.RECURSO_AEROPUERTOS);

        assertFalse(planes.isEmpty());
        final PlanDeVuelo primero = planes.get(0);
        assertEquals("SKBO", primero.getOrigen());
        assertEquals("SEQM", primero.getDestino());
        assertEquals(300, primero.getCapacidad());

        final List<Vuelo> vuelos = VueloLoader.expandirVuelos(planes, LocalDate.of(2026, 1, 2), 2, husos);
        assertEquals(planes.size() * 2, vuelos.size());
        assertTrue(vuelos.get(0).getIdVuelo().startsWith("VUE-"));
    }

    @Test
    void envio_loader_parsea_lineas_ok() {
        final List<Envio> envios = EnvioLoader.cargar(CargadorDatos.RECURSO_ENVIOS_EBCI, "EBCI", 10);

        assertEquals(10, envios.size());
        final Envio primero = envios.get(0);
        assertEquals("ENV-000000001", primero.getIdEnvio());
        assertEquals("EBCI", primero.getIdAeropuertoSalida());
        assertEquals("SUAA", primero.getIdAeropuertoLlegada());
        assertEquals(2, primero.getCantidadMaletas());
    }

    @Test
    void cargador_datos_arma_instancia_completa_ok() {
        final InstanciaProblema instancia = CargadorDatos.cargarInstanciaCompleta(
            LocalDate.of(2026, 1, 2),
            2,
            20
        );

        assertNotNull(instancia);
        assertFalse(instancia.getAeropuertos().isEmpty());
        assertFalse(instancia.getVuelos().isEmpty());
        assertFalse(instancia.getMaletasProcesadas().isEmpty());
        for (final MaletaProcesada maleta : instancia.getMaletasProcesadas()) {
            assertEquals("EBCI", maleta.getIdAeropuertoSalida());
            assertTrue(maleta.getPlazoMaximoDias() >= 1 && maleta.getPlazoMaximoDias() <= 2);
        }
    }
}
