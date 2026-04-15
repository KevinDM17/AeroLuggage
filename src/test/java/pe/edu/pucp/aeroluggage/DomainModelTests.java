package pe.edu.pucp.aeroluggage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.domain.Aerolinea;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Ciudad;
import pe.edu.pucp.aeroluggage.domain.Continente;
import pe.edu.pucp.aeroluggage.domain.Envio;
import pe.edu.pucp.aeroluggage.domain.Maleta;
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
        envio.setIdAeropuertoSalida("AEP-001");
        envio.setIdAeropuertoLlegada("AEP-002");
        envio.setFechaRegistro(fechaRegistro);
        envio.setCantidadMaletas(3);
        envio.setEstado("REGISTRADO");

        assertEquals("ENV-001", envio.getIdEnvio());
        assertEquals("AEP-001", envio.getIdAeropuertoSalida());
        assertEquals("AEP-002", envio.getIdAeropuertoLlegada());
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
        final Aeropuerto aeropuerto = new Aeropuerto(
            "AEP-001",
            "CIU-001",
            "Jorge Chavez",
            500,
            120,
            -77.1143f,
            -12.0219f
        );

        assertEquals("AEP-001", aeropuerto.getIdAeropuerto());
        assertEquals("CIU-001", aeropuerto.getIdCiudad());
        assertEquals("Jorge Chavez", aeropuerto.getNombre());
        assertEquals(500, aeropuerto.getCapacidadAlmacen());
        assertEquals(120, aeropuerto.getMaletasActuales());
        assertEquals(-77.1143f, aeropuerto.getLongitud());
        assertEquals(-12.0219f, aeropuerto.getLatitud());
    }

    @Test
    void maleta_constructor_completo_ok() {
        final Maleta maleta = new Maleta("MAL-001", "ENV-001", "REGISTRADA");

        assertEquals("MAL-001", maleta.getIdMaleta());
        assertEquals("ENV-001", maleta.getIdEnvio());
        assertEquals("REGISTRADA", maleta.getEstado());
        assertNotNull(maleta.toString());
    }

    @Test
    void ciudad_usa_continente_ok() {
        final Ciudad ciudad = new Ciudad("CIU-001", "Lima", Continente.AMERICA_DEL_SUR);

        assertEquals("CIU-001", ciudad.getIdCiudad());
        assertEquals("Lima", ciudad.getNombre());
        assertEquals(Continente.AMERICA_DEL_SUR, ciudad.getContinente());
        assertNotNull(ciudad.toString());
    }

    @Test
    void continente_valores_ok() {
        assertEquals(Continente.ASIA, Continente.valueOf("ASIA"));
        assertEquals(Continente.EUROPA, Continente.valueOf("EUROPA"));
        assertEquals(Continente.AMERICA_DEL_SUR, Continente.valueOf("AMERICA_DEL_SUR"));
        assertEquals(Continente.AMERICA_DEL_NORTE, Continente.valueOf("AMERICA_DEL_NORTE"));
        assertEquals(Continente.CENTROAMERICA, Continente.valueOf("CENTROAMERICA"));
        assertEquals(Continente.OCEANIA, Continente.valueOf("OCEANIA"));
        assertEquals(Continente.AFRICA, Continente.valueOf("AFRICA"));
    }

    @Test
    void metaheuristica_a_metodos_base_ok() {
        final MetaheuristicaA metaheuristicaA = new MetaheuristicaA();

        metaheuristicaA.ejecutar();
        metaheuristicaA.evaluar();

        assertNotNull(metaheuristicaA.toString());
    }
}
