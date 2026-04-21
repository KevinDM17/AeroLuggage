package pe.edu.pucp.aeroluggage.dominio.entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

class VueloInstanciaTest {

    @Test
    void cancelar_ok() {
        final VueloInstancia vueloInstancia = crearVueloInstancia();

        vueloInstancia.cancelar();

        assertEquals(EstadoVuelo.CANCELADO, vueloInstancia.getEstado());
        assertEquals(0, vueloInstancia.getCapacidadDisponible());
    }

    @Test
    void actualizar_capacidad_ok_normaliza_valor() {
        final VueloInstancia vueloInstancia = crearVueloInstancia();
        vueloInstancia.setCapacidadDisponible(120);

        vueloInstancia.actualizarCapacidad();

        assertEquals(50, vueloInstancia.getCapacidadDisponible());
    }

    @Test
    void actualizar_capacidad_ok_cancelado() {
        final VueloInstancia vueloInstancia = crearVueloInstancia();
        vueloInstancia.setEstado(EstadoVuelo.CANCELADO);
        vueloInstancia.setCapacidadDisponible(10);

        vueloInstancia.actualizarCapacidad();

        assertEquals(0, vueloInstancia.getCapacidadDisponible());
    }

    private VueloInstancia crearVueloInstancia() {
        return new VueloInstancia(
                "VI-1",
                "COD-1",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 20, 10, 0),
                50,
                20,
                crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR),
                crearAeropuerto("SCL", Continente.AMERICA_DEL_SUR),
                EstadoVuelo.PROGRAMADO
        );
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto, final Continente continente) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, continente);
        return new Aeropuerto(idAeropuerto, ciudad, 100, 0, 0F, 0F);
    }
}
