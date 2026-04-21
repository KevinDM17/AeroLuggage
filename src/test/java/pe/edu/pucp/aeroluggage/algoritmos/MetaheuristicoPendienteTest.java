package pe.edu.pucp.aeroluggage.algoritmos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.algoritmos.aco.ACO;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOConfiguracion;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOReporte;
import pe.edu.pucp.aeroluggage.algoritmos.ga.GA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;

class MetaheuristicoPendienteTest {

    @Test
    void aco_deberia_generar_una_solucion_consistente() {
        final Aeropuerto lima = crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR);
        final Aeropuerto madrid = crearAeropuerto("MAD", Continente.EUROPA);
        final Pedido pedido = new Pedido(
                "PED-1",
                lima,
                madrid,
                LocalDateTime.of(2026, 4, 20, 8, 0),
                1,
                "REGISTRADO"
        );
        pedido.registrarPedido();
        final Maleta maleta = new Maleta("MAL-1", pedido, pedido.getFechaRegistro(), null, "PENDIENTE");
        final VueloProgramado vueloDirecto = new VueloProgramado(
                "VP-1",
                "LA100",
                LocalDateTime.of(2026, 4, 20, 10, 0),
                LocalDateTime.of(2026, 4, 21, 7, 0),
                5,
                lima,
                madrid
        );
        final InstanciaProblema instancia = new InstanciaProblema(
                "IP-ACO-1",
                lista(maleta),
                lista(vueloDirecto),
                lista(lima, madrid)
        );

        final ACO aco = new ACO(crearConfiguracionBasica());

        aco.ejecutar(instancia);

        final Solucion solucion = aco.getUltimaSolucion();
        final ACOReporte reporte = aco.getUltimoReporte();
        assertNotNull(solucion);
        assertEquals(1, solucion.getSubrutas().size());
        final Ruta ruta = solucion.getSubrutas().get(0);
        assertEquals("MAL-1", ruta.getIdMaleta());
        assertEquals(1, ruta.getSubrutas().size());
        assertEquals("VP-1", ruta.getSubrutas().get(0).getIdVueloInstancia());
        assertEquals(1, reporte.getRutasFactibles());
        assertEquals(0, reporte.getRutasNoFactibles());
        assertTrue(Double.isFinite(aco.getUltimoCosto()));
    }

    @Test
    void aco_deberia_construir_una_ruta_con_escala() {
        final Aeropuerto lima = crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR);
        final Aeropuerto bogota = crearAeropuerto("BOG", Continente.AMERICA_DEL_SUR);
        final Aeropuerto madrid = crearAeropuerto("MAD", Continente.EUROPA);
        final Pedido pedido = new Pedido(
                "PED-2",
                lima,
                madrid,
                LocalDateTime.of(2026, 4, 20, 7, 0),
                1,
                "REGISTRADO"
        );
        pedido.registrarPedido();
        final Maleta maleta = new Maleta("MAL-2", pedido, pedido.getFechaRegistro(), null, "PENDIENTE");
        final VueloProgramado tramoUno = new VueloProgramado(
                "VP-2",
                "LA200",
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 20, 12, 0),
                4,
                lima,
                bogota
        );
        final VueloProgramado tramoDos = new VueloProgramado(
                "VP-3",
                "LA201",
                LocalDateTime.of(2026, 4, 20, 16, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0),
                4,
                bogota,
                madrid
        );
        final InstanciaProblema instancia = new InstanciaProblema(
                "IP-ACO-2",
                lista(maleta),
                lista(tramoUno, tramoDos),
                lista(lima, bogota, madrid)
        );

        final ACO aco = new ACO(crearConfiguracionBasica());

        aco.ejecutar(instancia);

        final Solucion solucion = aco.getUltimaSolucion();
        assertEquals(1, solucion.getSubrutas().size());
        assertEquals(2, solucion.getSubrutas().get(0).getSubrutas().size());
        assertEquals(1, aco.getUltimoReporte().getRutasFactibles());
    }

    @Disabled("GA sigue pendiente de implementación funcional.")
    @Test
    void ga_deberia_generar_una_solucion_consistente() {
        new GA();
    }

    private ACOConfiguracion crearConfiguracionBasica() {
        final ACOConfiguracion configuracion = new ACOConfiguracion();
        configuracion.setNts(1);
        configuracion.setMaxIter(5);
        configuracion.setNAnts(4);
        configuracion.setSemilla(17L);
        return configuracion;
    }

    @SafeVarargs
    private final <T> ArrayList<T> lista(final T... elementos) {
        final ArrayList<T> lista = new ArrayList<>();
        if (elementos == null) {
            return lista;
        }
        for (final T elemento : elementos) {
            lista.add(elemento);
        }
        return lista;
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto, final Continente continente) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, continente);
        return new Aeropuerto(idAeropuerto, ciudad, 20, 0, 0F, 0F);
    }
}
