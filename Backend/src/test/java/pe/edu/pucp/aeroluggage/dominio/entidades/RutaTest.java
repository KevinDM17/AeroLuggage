package pe.edu.pucp.aeroluggage.dominio.entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

class RutaTest {

    @Test
    void calcular_plazo_ok() {
        final Aeropuerto origen = crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR);
        final Aeropuerto escala = crearAeropuerto("BOG", Continente.AMERICA_DEL_SUR);
        final Aeropuerto destino = crearAeropuerto("MEX", Continente.AMERICA_DEL_NORTE);
        final ArrayList<VueloInstancia> subrutas = new ArrayList<>();
        subrutas.add(new VueloInstancia(
                "VI-1",
                "V1",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 20, 12, 0),
                100,
                50,
                origen,
                escala,
                EstadoVuelo.PROGRAMADO
        ));
        subrutas.add(new VueloInstancia(
                "VI-2",
                "V2",
                LocalDateTime.of(2026, 4, 20, 18, 0),
                LocalDateTime.of(2026, 4, 21, 20, 0),
                100,
                30,
                escala,
                destino,
                EstadoVuelo.PROGRAMADO
        ));
        final Ruta ruta = new Ruta("R-1", "M-1", 2, 0, subrutas, "PENDIENTE");

        final double duracion = ruta.calcularPlazo();

        assertEquals(1.5D, duracion, 0.0001D);
        assertEquals(1.5D, ruta.getDuracion(), 0.0001D);
    }

    @Test
    void replanificar_ok_actualiza_estado() {
        final ArrayList<VueloInstancia> subrutas = new ArrayList<>();
        subrutas.add(new VueloInstancia(
                "VI-3",
                "V3",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 20, 10, 0),
                80,
                10,
                crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR),
                crearAeropuerto("SCL", Continente.AMERICA_DEL_SUR),
                EstadoVuelo.PROGRAMADO
        ));
        final Ruta ruta = new Ruta("R-2", "M-2", 1, 0, subrutas, "PENDIENTE");

        ruta.replanificar();

        assertEquals("REPLANIFICADA", ruta.getEstado());
        assertEquals((2D / 24D), ruta.getDuracion(), 0.0001D);
    }

    @Test
    void replanificar_ok_sin_subrutas() {
        final Ruta ruta = new Ruta("R-3", "M-3", 1, 5, null, "PENDIENTE");

        ruta.replanificar();

        assertEquals("PENDIENTE_REPLANIFICACION", ruta.getEstado());
        assertEquals(0D, ruta.getDuracion(), 0.0001D);
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto, final Continente continente) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, continente);
        return new Aeropuerto(idAeropuerto, ciudad, 100, 0, 0F, 0F);
    }
}
