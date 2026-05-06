package pe.edu.pucp.aeroluggage.algoritmos.aco;

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
}
