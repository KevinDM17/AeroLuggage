package pe.edu.pucp.aeroluggage.dominio.entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

class RutaTest {

    @Test
    void calcular_plazo_ok() {
        final Aeropuerto origen = crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR);
        final Aeropuerto escala = crearAeropuerto("BOG", Continente.AMERICA_DEL_SUR);
        final Aeropuerto destino = crearAeropuerto("MEX", Continente.AMERICA_DEL_NORTE);
        final VueloInstancia v1 = new VueloInstancia(
                "VI-1",
                "V1",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 20, 12, 0),
                100,
                50,
                origen,
                escala,
                EstadoVuelo.PROGRAMADO
        );
        final VueloInstancia v2 = new VueloInstancia(
                "VI-2",
                "V2",
                LocalDateTime.of(2026, 4, 20, 18, 0),
                LocalDateTime.of(2026, 4, 21, 20, 0),
                100,
                30,
                escala,
                destino,
                EstadoVuelo.PROGRAMADO
        );
        final List<String> ids = List.of(v1.getIdVueloInstancia(), v2.getIdVueloInstancia());
        final Ruta ruta = new Ruta("R-1", "M-1", 2, 0, ids, EstadoRuta.PLANIFICADA);

        final Map<String, VueloInstancia> vueloIndex = new HashMap<>();
        vueloIndex.put(v1.getIdVueloInstancia(), v1);
        vueloIndex.put(v2.getIdVueloInstancia(), v2);
        final double duracion = ruta.calcularPlazo(vueloIndex);

        assertEquals(1.5D, duracion, 0.0001D);
        assertEquals(1.5D, ruta.getDuracion(), 0.0001D);
    }

    @Test
    void replanificar_ok_actualiza_estado() {
        final VueloInstancia v = new VueloInstancia(
                "VI-3",
                "V3",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 20, 10, 0),
                80,
                10,
                crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR),
                crearAeropuerto("SCL", Continente.AMERICA_DEL_SUR),
                EstadoVuelo.PROGRAMADO
        );
        final List<String> ids = List.of(v.getIdVueloInstancia());
        final Ruta ruta = new Ruta("R-2", "M-2", 1, 0, ids, EstadoRuta.REPLANIFICADA);

        final Map<String, VueloInstancia> vueloIndex = new HashMap<>();
        vueloIndex.put(v.getIdVueloInstancia(), v);
        ruta.replanificar(vueloIndex);

        assertEquals("REPLANIFICADA", ruta.getEstado());
        assertEquals((2D / 24D), ruta.getDuracion(), 0.0001D);
    }

    @Test
    void replanificar_ok_sin_subrutas() {
        final Ruta ruta = new Ruta("R-3", "M-3", 1, 5, null, EstadoRuta.PLANIFICADA);

        ruta.replanificar(null);

        assertEquals("PENDIENTE_REPLANIFICACION", ruta.getEstado());
        assertEquals(0D, ruta.getDuracion(), 0.0001D);
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto, final Continente continente) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, continente);
        return new Aeropuerto(idAeropuerto, ciudad, 100, 0, 0F, 0F);
    }
}
