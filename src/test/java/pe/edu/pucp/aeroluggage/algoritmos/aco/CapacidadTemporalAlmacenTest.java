package pe.edu.pucp.aeroluggage.algoritmos.aco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class CapacidadTemporalAlmacenTest {

    @Test
    void puede_reservar_detecta_solapamiento_en_intervalo_completo() {
        final CapacidadTemporalAlmacen capacidad = new CapacidadTemporalAlmacen(1);
        final LocalDateTime inicio = LocalDateTime.of(2027, 4, 11, 10, 0);
        final LocalDateTime fin = LocalDateTime.of(2027, 4, 11, 12, 0);
        capacidad.reservar(inicio, fin);

        assertFalse(capacidad.puedeReservar(
                LocalDateTime.of(2027, 4, 11, 11, 0),
                LocalDateTime.of(2027, 4, 11, 13, 0)
        ));
        assertTrue(capacidad.puedeReservar(
                LocalDateTime.of(2027, 4, 11, 12, 0),
                LocalDateTime.of(2027, 4, 11, 14, 0)
        ));
    }

    @Test
    void liberar_aumenta_disponibilidad_despues_del_timestamp() {
        // capacidadBase=0: aeropuerto lleno con maletasActuales
        final CapacidadTemporalAlmacen capacidad = new CapacidadTemporalAlmacen(0);
        final LocalDateTime salida = LocalDateTime.of(2027, 4, 11, 10, 0);
        capacidad.liberar(salida);

        assertEquals(0, capacidad.disponibleEn(LocalDateTime.of(2027, 4, 11, 9, 59)));
        assertEquals(1, capacidad.disponibleEn(LocalDateTime.of(2027, 4, 11, 10, 0)));
        assertEquals(1, capacidad.disponibleEn(LocalDateTime.of(2027, 4, 11, 12, 0)));
    }

    @Test
    void liberar_no_afecta_disponibilidad_antes_del_timestamp() {
        final CapacidadTemporalAlmacen capacidad = new CapacidadTemporalAlmacen(1);
        final LocalDateTime salida = LocalDateTime.of(2027, 4, 11, 14, 0);
        capacidad.liberar(salida);

        assertEquals(1, capacidad.disponibleEn(LocalDateTime.of(2027, 4, 11, 13, 59)));
        assertEquals(2, capacidad.disponibleEn(LocalDateTime.of(2027, 4, 11, 14, 0)));
    }

    @Test
    void liberar_permite_reservar_en_aeropuerto_lleno_post_liberacion() {
        // Aeropuerto lleno (base=0), maleta sale a las 10:00 → slot libre desde 10:00
        final CapacidadTemporalAlmacen capacidad = new CapacidadTemporalAlmacen(0);
        final LocalDateTime salida = LocalDateTime.of(2027, 4, 11, 10, 0);
        capacidad.liberar(salida);

        assertFalse(capacidad.puedeReservar(
                LocalDateTime.of(2027, 4, 11, 9, 0),
                LocalDateTime.of(2027, 4, 11, 11, 0)
        ));
        assertTrue(capacidad.puedeReservar(
                LocalDateTime.of(2027, 4, 11, 10, 0),
                LocalDateTime.of(2027, 4, 11, 12, 0)
        ));
    }

    @Test
    void liberar_no_permite_doble_reserva_con_una_sola_liberacion() {
        final CapacidadTemporalAlmacen capacidad = new CapacidadTemporalAlmacen(0);
        capacidad.liberar(LocalDateTime.of(2027, 4, 11, 10, 0));
        capacidad.reservar(
                LocalDateTime.of(2027, 4, 11, 10, 0),
                LocalDateTime.of(2027, 4, 11, 12, 0)
        );

        // El slot ya fue usado; una segunda reserva solapada no debe permitirse
        assertFalse(capacidad.puedeReservar(
                LocalDateTime.of(2027, 4, 11, 11, 0),
                LocalDateTime.of(2027, 4, 11, 13, 0)
        ));
    }

    @Test
    void clonar_preserva_liberaciones() {
        final CapacidadTemporalAlmacen original = new CapacidadTemporalAlmacen(0);
        final LocalDateTime salida = LocalDateTime.of(2027, 4, 11, 10, 0);
        original.liberar(salida);

        final CapacidadTemporalAlmacen clon = original.clonar();

        assertEquals(1, clon.disponibleEn(LocalDateTime.of(2027, 4, 11, 10, 0)));
        // Modificar el clon no afecta al original
        clon.liberar(LocalDateTime.of(2027, 4, 11, 12, 0));
        assertEquals(1, original.disponibleEn(LocalDateTime.of(2027, 4, 11, 12, 0)));
        assertEquals(2, clon.disponibleEn(LocalDateTime.of(2027, 4, 11, 12, 0)));
    }
}
