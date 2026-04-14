package pe.edu.pucp.aeroluggage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.domain.Aerolinea;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Envio;
import pe.edu.pucp.aeroluggage.domain.MetaheuristicaA;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

class DomainModelTests {
    @Test
    void aerolinea_constructor_completo_ok() {
        final Aerolinea aerolinea = new Aerolinea("AER-001", "Tasf Air");

        assertEquals("AER-001", aerolinea.getIdAerolinea());
        assertEquals("Tasf Air", aerolinea.getNombre());
        assertNotNull(aerolinea.toString());
    }

    @Test
    void envio_constructor_vacio_y_setters_ok() {
        final Date fechaRegistro = new Date();
        final Envio envio = new Envio();

        envio.setIdEnvio("ENV-001");
        envio.setFechaRegistro(fechaRegistro);
        envio.setCantidadMaletas(3);
        envio.setEstado("REGISTRADO");

        assertEquals("ENV-001", envio.getIdEnvio());
        assertEquals(fechaRegistro, envio.getFechaRegistro());
        assertEquals(3, envio.getCantidadMaletas());
        assertEquals("REGISTRADO", envio.getEstado());
    }

    @Test
    void vuelo_constructor_completo_ok() {
        final Date fechaSalida = new Date();
        final Date fechaLlegada = new Date();
        final Vuelo vuelo = new Vuelo("VUE-001", "TA-100", fechaSalida, fechaLlegada, 80, 25, "PROGRAMADO");

        assertEquals("VUE-001", vuelo.getIdVuelo());
        assertEquals("TA-100", vuelo.getCodigo());
        assertEquals(fechaSalida, vuelo.getFechaSalida());
        assertEquals(fechaLlegada, vuelo.getFechaLlegada());
        assertEquals(80, vuelo.getCapacidadMaxima());
        assertEquals(25, vuelo.getCapacidadDisponible());
        assertEquals("PROGRAMADO", vuelo.getEstado());
    }

    @Test
    void aeropuerto_constructor_completo_ok() {
        final Aeropuerto aeropuerto = new Aeropuerto("AEP-001", "Jorge Chavez", 500, 120, -77.1143f, -12.0219f);

        assertEquals("AEP-001", aeropuerto.getIdAeropuerto());
        assertEquals("Jorge Chavez", aeropuerto.getNombre());
        assertEquals(500, aeropuerto.getCapacidadAlmacen());
        assertEquals(120, aeropuerto.getMaletasActuales());
        assertEquals(-77.1143f, aeropuerto.getLongitud());
        assertEquals(-12.0219f, aeropuerto.getLatitud());
    }

    @Test
    void metaheuristica_a_hereda_nombre_ok() {
        final MetaheuristicaA metaheuristicaA = new MetaheuristicaA("Metaheuristica A");

        assertEquals("Metaheuristica A", metaheuristicaA.getNombre());
        assertNotNull(metaheuristicaA.toString());
    }
}
