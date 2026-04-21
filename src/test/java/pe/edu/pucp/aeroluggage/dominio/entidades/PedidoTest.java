package pe.edu.pucp.aeroluggage.dominio.entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.dominio.enums.Continente;

class PedidoTest {

    @Test
    void calcular_fecha_hora_plazo_ok_mismo_continente() {
        final LocalDateTime fechaRegistro = LocalDateTime.of(2026, 4, 20, 10, 0);
        final Pedido pedido = new Pedido(
                "PED-1",
                crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR),
                crearAeropuerto("BOG", Continente.AMERICA_DEL_SUR),
                fechaRegistro,
                3,
                "PENDIENTE"
        );

        final LocalDateTime fechaHoraPlazo = pedido.calcularFechaHoraPlazo();

        assertEquals(fechaRegistro.plusDays(1), fechaHoraPlazo);
        assertEquals(fechaRegistro.plusDays(1), pedido.getFechaHoraPlazo());
    }

    @Test
    void registrar_pedido_ok_intercontinental() {
        final LocalDateTime fechaRegistro = LocalDateTime.of(2026, 4, 20, 10, 0);
        final Pedido pedido = new Pedido(
                "PED-2",
                crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR),
                crearAeropuerto("MAD", Continente.EUROPA),
                null,
                fechaRegistro,
                2,
                null
        );

        pedido.registrarPedido();

        assertEquals("REGISTRADO", pedido.getEstado());
        assertEquals(fechaRegistro.plusDays(2), pedido.getFechaHoraPlazo());
    }

    @Test
    void registrar_pedido_ok_asigna_fecha_si_no_existe() {
        final Pedido pedido = new Pedido();
        pedido.setAeropuertoOrigen(crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR));
        pedido.setAeropuertoDestino(crearAeropuerto("SCL", Continente.AMERICA_DEL_SUR));

        pedido.registrarPedido();

        assertNotNull(pedido.getFechaRegistro());
        assertNotNull(pedido.getFechaHoraPlazo());
        assertEquals("REGISTRADO", pedido.getEstado());
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto, final Continente continente) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, continente);
        return new Aeropuerto(idAeropuerto, ciudad, 100, 0, 0F, 0F);
    }
}
