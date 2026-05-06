package pe.edu.pucp.aeroluggage.simulacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

class SimulacionTemporalRunnerTest {

    @Test
    void calcular_configuracion_programada_ok() {
        final Properties params = new Properties();
        params.setProperty("simulacion.programada.sa.segundos", "60");
        params.setProperty("simulacion.programada.k", "70");

        final long saMs = SimulacionTemporalRunner.calcularSaltoPlanificacionMs(params);
        final Duration sc = SimulacionTemporalRunner.calcularSaltoConsumoDatos(params);

        assertEquals(60000L, saMs);
        assertEquals(Duration.ofMinutes(70L), sc);
    }

    @Test
    void obtener_primer_momento_pedidos_ignora_pedidos_sin_fecha() {
        final Pedido pedidoSinFecha = crearPedido("PED-0", null, 1);
        final Pedido pedidoConFecha = crearPedido("PED-1", LocalDateTime.of(2027, 4, 11, 10, 5), 2);

        final LocalDateTime primerMomento = SimulacionTemporalRunner.obtenerPrimerMomentoPedidos(
                List.of(pedidoSinFecha, pedidoConFecha)
        );

        assertEquals(LocalDateTime.of(2027, 4, 11, 10, 5), primerMomento);
    }

    @Test
    void incorporar_pedidos_hasta_momento_agrega_maletas_del_ciclo() {
        final Pedido pedidoUno = crearPedido("PED-1", LocalDateTime.of(2027, 4, 11, 10, 5), 2);
        final Pedido pedidoDos = crearPedido("PED-2", LocalDateTime.of(2027, 4, 11, 11, 20), 1);
        final List<Pedido> pedidos = List.of(pedidoUno, pedidoDos);
        final Map<String, List<Maleta>> maletasPorPedido = new HashMap<>();
        maletasPorPedido.put("PED-1", List.of(
                crearMaleta("M-1", pedidoUno),
                crearMaleta("M-2", pedidoUno)
        ));
        maletasPorPedido.put("PED-2", List.of(crearMaleta("M-3", pedidoDos)));

        final List<Maleta> registradas = new ArrayList<>();

        final int siguienteIndice = SimulacionTemporalRunner.incorporarPedidosHastaMomento(
                pedidos,
                0,
                LocalDateTime.of(2027, 4, 11, 11, 15),
                maletasPorPedido,
                registradas
        );

        assertEquals(1, siguienteIndice);
        assertEquals(2, registradas.size());
    }

    @Test
    void excede_ventana_planificacion_ok() {
        assertTrue(SimulacionTemporalRunner.excedeVentanaPlanificacion(61000L, 60000L));
        assertFalse(SimulacionTemporalRunner.excedeVentanaPlanificacion(60000L, 60000L));
    }

    @Test
    void ya_salio_de_data_activa_en_umbral_de_diez_minutos() {
        final LocalDateTime llegadaFinal = LocalDateTime.of(2027, 4, 11, 10, 0);
        final VueloInstancia ultimoVuelo = new VueloInstancia(
                "VI-1",
                "LIM-BOG",
                llegadaFinal.minusHours(2),
                llegadaFinal,
                10,
                10,
                crearAeropuerto("LIM"),
                crearAeropuerto("BOG"),
                EstadoVuelo.FINALIZADO
        );

        assertFalse(SimulacionTemporalRunner.yaSalioDeDataActiva(
                List.of(ultimoVuelo),
                llegadaFinal.plusMinutes(9),
                10L
        ));
        assertTrue(SimulacionTemporalRunner.yaSalioDeDataActiva(
                List.of(ultimoVuelo),
                llegadaFinal.plusMinutes(10),
                10L
        ));
    }

    @Test
    void depurar_maletas_finalizadas_remueve_data_activa_y_rutas() {
        final Pedido pedido = crearPedido("PED-1", LocalDateTime.of(2027, 4, 11, 10, 5), 2);
        final Maleta maletaFinalizada = crearMaleta("M-1", pedido);
        final Maleta maletaActiva = crearMaleta("M-2", pedido);
        final List<Maleta> registradas = new ArrayList<>(List.of(maletaFinalizada, maletaActiva));
        final Map<String, Object> rutasProgramadas = new HashMap<>();
        rutasProgramadas.put("M-1", new Object());
        rutasProgramadas.put("M-2", new Object());
        final Set<String> maletasFinalizadas = new HashSet<>(Set.of("M-1"));

        final int removidas = SimulacionTemporalRunner.depurarMaletasFinalizadas(
                registradas,
                rutasProgramadas,
                maletasFinalizadas
        );

        assertEquals(1, removidas);
        assertEquals(1, registradas.size());
        assertEquals("M-2", registradas.get(0).getIdMaleta());
        assertFalse(rutasProgramadas.containsKey("M-1"));
        assertTrue(rutasProgramadas.containsKey("M-2"));
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

    private Maleta crearMaleta(final String idMaleta, final Pedido pedido) {
        return new Maleta(idMaleta, pedido, pedido.getFechaRegistro(), null, "EN_ALMACEN");
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, Continente.AMERICA_DEL_SUR);
        return new Aeropuerto(idAeropuerto, ciudad, 100, 0, 0F, 0F);
    }
}
