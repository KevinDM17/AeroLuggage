package pe.edu.pucp.aeroluggage.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.cargador.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;

class CargadorDatosPruebaTest {

    @Test
    void calcular_demanda_maletas_por_dia_ok() {
        final List<Pedido> pedidos = List.of(
                crearPedido("PED-1", LocalDateTime.of(2026, 4, 20, 10, 0), 3),
                crearPedido("PED-2", LocalDateTime.of(2026, 4, 20, 12, 0), 5),
                crearPedido("PED-3", LocalDateTime.of(2026, 4, 21, 9, 0), 4)
        );

        final Map<LocalDate, Integer> demandaPorDia = CargadorDatosPrueba.calcularDemandaMaletasPorDia(pedidos);

        assertEquals(2, demandaPorDia.size());
        assertEquals(8, demandaPorDia.get(LocalDate.of(2026, 4, 20)));
        assertEquals(4, demandaPorDia.get(LocalDate.of(2026, 4, 21)));
    }

    @Test
    void encontrar_dias_con_mayor_demanda_ok_un_dia() {
        final List<Pedido> pedidos = List.of(
                crearPedido("PED-1", LocalDateTime.of(2026, 4, 20, 10, 0), 3),
                crearPedido("PED-2", LocalDateTime.of(2026, 4, 20, 12, 0), 5),
                crearPedido("PED-3", LocalDateTime.of(2026, 4, 21, 9, 0), 4)
        );

        final Map<LocalDate, Integer> diasConMayorDemanda =
                CargadorDatosPrueba.encontrarDiasConMayorDemanda(pedidos);

        assertEquals(1, diasConMayorDemanda.size());
        assertEquals(8, diasConMayorDemanda.get(LocalDate.of(2026, 4, 20)));
    }

    @Test
    void encontrar_dias_con_mayor_demanda_ok_empate() {
        final List<Pedido> pedidos = List.of(
                crearPedido("PED-1", LocalDateTime.of(2026, 4, 20, 10, 0), 4),
                crearPedido("PED-2", LocalDateTime.of(2026, 4, 21, 12, 0), 4),
                crearPedido("PED-3", LocalDateTime.of(2026, 4, 22, 9, 0), 2)
        );

        final Map<LocalDate, Integer> diasConMayorDemanda =
                CargadorDatosPrueba.encontrarDiasConMayorDemanda(pedidos);

        assertEquals(2, diasConMayorDemanda.size());
        assertEquals(4, diasConMayorDemanda.get(LocalDate.of(2026, 4, 20)));
        assertEquals(4, diasConMayorDemanda.get(LocalDate.of(2026, 4, 21)));
    }

    @Test
    void encontrar_dias_con_mayor_demanda_ok_vacio() {
        final Map<LocalDate, Integer> diasConMayorDemanda =
                CargadorDatosPrueba.encontrarDiasConMayorDemanda(List.of());

        assertTrue(diasConMayorDemanda.isEmpty());
    }

    @Test
    void encontrar_dias_con_mayor_demanda_ignora_fechas_nulas() {
        final Pedido pedidoSinFecha = crearPedido("PED-1", LocalDateTime.of(2026, 4, 20, 10, 0), 3);
        pedidoSinFecha.setFechaRegistro(null);
        final List<Pedido> pedidos = List.of(
                pedidoSinFecha,
                crearPedido("PED-2", LocalDateTime.of(2026, 4, 21, 12, 0), 5)
        );

        final Map<LocalDate, Integer> diasConMayorDemanda =
                CargadorDatosPrueba.encontrarDiasConMayorDemanda(pedidos);

        assertEquals(1, diasConMayorDemanda.size());
        assertEquals(5, diasConMayorDemanda.get(LocalDate.of(2026, 4, 21)));
    }

    private Pedido crearPedido(final String idPedido, final LocalDateTime fechaRegistro, final int cantidadMaletas) {
        return new Pedido(
                idPedido,
                crearAeropuerto("LIM"),
                crearAeropuerto("BOG"),
                fechaRegistro,
                cantidadMaletas,
                "REGISTRADO"
        );
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, Continente.AMERICA_DEL_SUR);
        return new Aeropuerto(idAeropuerto, ciudad, 100, 0, 0F, 0F);
    }
}
