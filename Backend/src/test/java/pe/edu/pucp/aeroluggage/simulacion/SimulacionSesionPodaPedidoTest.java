package pe.edu.pucp.aeroluggage.simulacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.config.SistemaConfiguracion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

class SimulacionSesionPodaPedidoTest {

    @Test
    void poda_pedido_entregado_despues_de_dos_horas_simuladas() {
        final SistemaConfiguracion sistemaConfiguracion = new SistemaConfiguracion();
        sistemaConfiguracion.setUmbralConfirmacionMinutos(30);
        final SimulacionSnapshotService snapshotService = new SimulacionSnapshotService(sistemaConfiguracion);

        final LocalDate fechaInicio = LocalDate.of(2026, 1, 1);
        final LocalTime horaInicio = LocalTime.MIDNIGHT;
        final SimulacionSesion sesion = new SimulacionSesion("S-1", fechaInicio, horaInicio, 3, 240000L, 120, 120);

        final Aeropuerto origen = aeropuerto("LIM", Continente.AMERICA_DEL_SUR);
        final Aeropuerto destino = aeropuerto("MEX", Continente.AMERICA_DEL_NORTE);
        final Pedido pedido = new Pedido(
                "P-1",
                origen,
                destino,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0),
                1,
                EstadoPedido.REGISTRADO
        );
        final Maleta maleta = new Maleta(
                "M-1",
                pedido,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null,
                EstadoMaleta.EN_ALMACEN
        );
        final VueloInstancia vuelo = new VueloInstancia(
                "V-1",
                "AR100",
                LocalDateTime.of(2026, 1, 1, 0, 30),
                LocalDateTime.of(2026, 1, 1, 1, 0),
                10,
                10,
                origen,
                destino,
                EstadoVuelo.PROGRAMADO
        );
        final Ruta ruta = new Ruta("R-1", "M-1", 1D, 0D, List.of(vuelo), EstadoRuta.PLANIFICADA);

        sesion.setSnapshotData(
                List.of(origen, destino),
                List.of(),
                List.of(vuelo),
                List.of(pedido),
                List.of(maleta),
                List.of(ruta)
        );
        sesion.construirIndiceEventos(sistemaConfiguracion.getUmbralConfirmacionMinutos());

        final LocalDateTime entregaMaleta = LocalDateTime.of(2026, 1, 1, 1, 10);
        sesion.getCurrentSimTimeUtc().set(entregaMaleta);
        snapshotService.recalcularEstadoSesion(sesion);

        assertEquals(EstadoPedido.ENTREGADO, pedido.getEstado());
        assertEquals(entregaMaleta, pedido.getFechaEntrega());
        assertNull(sesion.getMaletasPorId().get("M-1"));
        final Ruta rutaFria = sesion.getRutaPorMaleta("M-1");
        assertNotNull(rutaFria);
        assertEquals(entregaMaleta, rutaFria.getFechaEntrega());
        final Maleta maletaFria = sesion.getMaletas().stream()
                .filter(item -> "M-1".equals(item.getIdMaleta()))
                .findFirst()
                .orElse(null);
        assertNotNull(maletaFria);
        assertEquals(entregaMaleta, maletaFria.getFechaLlegada());

        sesion.podarEntidadesAnteriores(entregaMaleta.plusHours(1).plusMinutes(59));
        assertEquals(1, sesion.getPedidos().size());
        assertNotNull(sesion.getRutaPorMaleta("M-1"));
        assertTrue(sesion.getMaletas().stream().anyMatch(item -> "M-1".equals(item.getIdMaleta())));

        sesion.podarEntidadesAnteriores(entregaMaleta.plusHours(2));
        assertTrue(sesion.getPedidos().isEmpty());
        assertNull(sesion.getRutaPorMaleta("M-1"));
        assertTrue(sesion.getMaletas().stream().noneMatch(item -> "M-1".equals(item.getIdMaleta())));
        assertTrue(sesion.getMaletasPorVentana().values().stream()
                .flatMap(List::stream)
                .noneMatch(item -> item != null && "M-1".equals(item.getIdMaleta())));
    }

    private static Aeropuerto aeropuerto(final String idAeropuerto, final Continente continente) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, continente);
        return new Aeropuerto(idAeropuerto, ciudad, 50, 0, 0F, 0F, 0);
    }
}
