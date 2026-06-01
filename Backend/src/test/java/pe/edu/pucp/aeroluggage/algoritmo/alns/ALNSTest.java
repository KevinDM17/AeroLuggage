package pe.edu.pucp.aeroluggage.algoritmo.alns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

class ALNSTest {

    @Test
    void alns_prioriza_maleta_con_menor_holgura_inicial() {
        final Aeropuerto lima = aeropuerto("LIM", Continente.AMERICA_DEL_SUR, 20, 0);
        final Aeropuerto bogota = aeropuerto("BOG", Continente.AMERICA_DEL_SUR, 20, 0);
        final LocalDateTime evaluacion = LocalDateTime.of(2026, 4, 20, 8, 0);

        final Pedido urgente = pedido("PED-U", lima, bogota, evaluacion.minusMinutes(30), evaluacion.plusHours(6));
        final Pedido flexible = pedido("PED-F", lima, bogota, evaluacion.minusMinutes(20), evaluacion.plusHours(12));
        final Maleta maletaUrgente = new Maleta("M-U", urgente, urgente.getFechaRegistro(), null, "EN_ALMACEN");
        final Maleta maletaFlexible = new Maleta("M-F", flexible, flexible.getFechaRegistro(), null, "EN_ALMACEN");

        final VueloInstancia vuelo = vuelo("V-1", lima, bogota,
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 20, 12, 0),
                1);

        final InstanciaProblema instancia = instancia(List.of(maletaUrgente, maletaFlexible), List.of(vuelo), List.of(lima, bogota));
        instancia.setFechaEvaluacion(evaluacion);

        final ALNS alns = new ALNS();
        alns.ejecutar(instancia);

        final Solucion solucion = alns.getMejorSolucion();
        assertNotNull(solucion);
        assertEquals(1, solucion.getSolucion().size());
        assertEquals("M-U", solucion.getSolucion().get(0).getIdMaleta());
    }

    @Test
    void alns_respeta_rutas_comprometidas_como_consumo_base() {
        final Aeropuerto lima = aeropuerto("LIM", Continente.AMERICA_DEL_SUR, 20, 0);
        final Aeropuerto bogota = aeropuerto("BOG", Continente.AMERICA_DEL_SUR, 20, 0);
        final LocalDateTime evaluacion = LocalDateTime.of(2026, 4, 20, 8, 0);

        final Pedido comprometidoPedido = pedido("PED-C", lima, bogota, evaluacion.minusHours(1), evaluacion.plusHours(8));
        final Maleta comprometida = new Maleta("M-C", comprometidoPedido, comprometidoPedido.getFechaRegistro(), null, "EN_ALMACEN");
        final Pedido pendientePedido = pedido("PED-P", lima, bogota, evaluacion.minusMinutes(15), evaluacion.plusHours(9));
        final Maleta pendiente = new Maleta("M-P", pendientePedido, pendientePedido.getFechaRegistro(), null, "EN_ALMACEN");

        final VueloInstancia vuelo = vuelo("V-1", lima, bogota,
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 20, 12, 0),
                1);
        final Ruta comprometidaRuta = ALNSUtil.crearRuta("R00000001", comprometida, List.of(vuelo), EstadoRuta.PLANIFICADA);

        final InstanciaProblema instancia = instancia(List.of(pendiente), List.of(vuelo), List.of(lima, bogota));
        instancia.setFechaEvaluacion(evaluacion);
        instancia.setRutasComprometidas(List.of(comprometidaRuta));

        final ALNS alns = new ALNS();
        alns.ejecutar(instancia);

        final Solucion solucion = alns.getMejorSolucion();
        assertTrue(solucion == null || solucion.getSolucion().isEmpty());
    }

    @Test
    void adaptatividad_actualiza_pesos_por_segmento() {
        final ALNSAdaptativo adaptativo = new ALNSAdaptativo(List.of("A", "B"));
        final ParametrosALNS parametros = ParametrosALNS.porDefecto();
        parametros.setRho(0.5D);
        parametros.setPesoMinimoOperador(0.1D);

        adaptativo.registrarUso("A", 10.0D);
        adaptativo.registrarUso("A", 6.0D);
        adaptativo.registrarUso("B", 2.0D);
        adaptativo.actualizarPesos(parametros);

        final Random random = new Random(7L);
        int seleccionA = 0;
        for (int i = 0; i < 200; i++) {
            if ("A".equals(adaptativo.seleccionar(random))) {
                seleccionA++;
            }
        }
        assertTrue(seleccionA > 100);
    }

    private InstanciaProblema instancia(final List<Maleta> maletas,
                                        final List<VueloInstancia> vuelos,
                                        final List<Aeropuerto> aeropuertos) {
        final InstanciaProblema instancia = new InstanciaProblema(
                "IP-ALNS",
                new ArrayList<>(maletas),
                new ArrayList<VueloProgramado>(),
                new ArrayList<>(vuelos),
                new ArrayList<>(aeropuertos)
        );
        instancia.construirGrafo();
        return instancia;
    }

    private Pedido pedido(final String id,
                          final Aeropuerto origen,
                          final Aeropuerto destino,
                          final LocalDateTime registro,
                          final LocalDateTime plazo) {
        return new Pedido(id, origen, destino, plazo, registro, 1, "REGISTRADO");
    }

    private VueloInstancia vuelo(final String id,
                                 final Aeropuerto origen,
                                 final Aeropuerto destino,
                                 final LocalDateTime salida,
                                 final LocalDateTime llegada,
                                 final int capacidad) {
        final VueloProgramado programado = new VueloProgramado(
                "VP-" + id,
                id,
                LocalDateTime.of(LocalDate.of(2026, 4, 20), LocalTime.of(salida.getHour(), salida.getMinute())),
                LocalDateTime.of(LocalDate.of(2026, 4, 20), LocalTime.of(llegada.getHour(), llegada.getMinute())),
                capacidad,
                origen,
                destino
        );
        return new VueloInstancia(id, programado, salida.toLocalDate(), salida, llegada, capacidad, capacidad, EstadoVuelo.PROGRAMADO);
    }

    private Aeropuerto aeropuerto(final String id,
                                  final Continente continente,
                                  final int capacidadAlmacen,
                                  final int maletasActuales) {
        return new Aeropuerto(id, new Ciudad("C-" + id, id, continente), capacidadAlmacen, maletasActuales, 0F, 0F);
    }
}
