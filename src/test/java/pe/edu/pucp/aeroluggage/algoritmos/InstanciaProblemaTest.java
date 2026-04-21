package pe.edu.pucp.aeroluggage.algoritmos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;

class InstanciaProblemaTest {

    @Test
    void buscar_elementos_por_id_ok() {
        final Aeropuerto aeropuertoOrigen = crearAeropuerto("LIM", Continente.AMERICA_DEL_SUR);
        final Aeropuerto aeropuertoDestino = crearAeropuerto("MAD", Continente.EUROPA);
        final Pedido pedido = new Pedido(
                "PED-1",
                aeropuertoOrigen,
                aeropuertoDestino,
                LocalDateTime.of(2026, 4, 20, 10, 0),
                1,
                "REGISTRADO"
        );
        final Maleta maleta = new Maleta("MAL-1", pedido, LocalDateTime.of(2026, 4, 20, 10, 0), null, "EN_RUTA");
        final VueloProgramado vueloProgramado = new VueloProgramado(
                "VP-1",
                "V-1",
                LocalDateTime.of(2026, 4, 20, 14, 0),
                LocalDateTime.of(2026, 4, 20, 22, 0),
                200,
                aeropuertoOrigen,
                aeropuertoDestino
        );
        final ArrayList<Maleta> maletas = new ArrayList<>();
        maletas.add(maleta);
        final ArrayList<VueloProgramado> vuelos = new ArrayList<>();
        vuelos.add(vueloProgramado);
        final ArrayList<Aeropuerto> aeropuertos = new ArrayList<>();
        aeropuertos.add(aeropuertoOrigen);
        aeropuertos.add(aeropuertoDestino);
        final InstanciaProblema instanciaProblema = new InstanciaProblema(
                "IP-1",
                maletas,
                vuelos,
                aeropuertos
        );

        assertEquals(pedido, instanciaProblema.buscarPedido("PED-1"));
        assertEquals(vueloProgramado, instanciaProblema.buscarVueloProgramado("VP-1"));
        assertEquals(aeropuertoDestino, instanciaProblema.buscarAeropuerto("MAD"));
        assertNull(instanciaProblema.buscarPedido("NO-EXISTE"));
    }

    @Test
    void to_string_ok() {
        final InstanciaProblema instanciaProblema = new InstanciaProblema();
        instanciaProblema.setIdInstanciaProblema("IP-2");

        final String descripcion = instanciaProblema.toString();

        assertNotNull(descripcion);
        assertTrue(descripcion.contains("IP-2"));
    }

    private Aeropuerto crearAeropuerto(final String idAeropuerto, final Continente continente) {
        final Ciudad ciudad = new Ciudad("CIU-" + idAeropuerto, "Ciudad " + idAeropuerto, continente);
        return new Aeropuerto(idAeropuerto, ciudad, 100, 0, 0F, 0F);
    }
}
