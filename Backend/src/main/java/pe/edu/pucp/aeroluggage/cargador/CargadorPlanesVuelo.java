package pe.edu.pucp.aeroluggage.cargador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

public final class CargadorPlanesVuelo {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int SEGMENTOS_ESPERADOS = 5;

    private CargadorPlanesVuelo() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<VueloProgramado> cargar(final Path archivo, final Map<String, Aeropuerto> aeropuertosPorIcao) {
        final List<String> lineas = leerLineas(archivo);
        final List<VueloProgramado> vuelos = new ArrayList<>(lineas.size());

        int secuencia = 1;
        for (final String lineaCruda : lineas) {
            final String linea = lineaCruda.trim();
            if (linea.isEmpty()) {
                continue;
            }
            final String[] partes = linea.split("-");
            if (partes.length != SEGMENTOS_ESPERADOS) {
                continue;
            }
            final String icaoOrigen = partes[0].trim();
            final String icaoDestino = partes[1].trim();
            final Aeropuerto origen = aeropuertosPorIcao.get(icaoOrigen);
            final Aeropuerto destino = aeropuertosPorIcao.get(icaoDestino);
            if (origen == null || destino == null) {
                continue;
            }
            final LocalTime horaSalida = LocalTime.parse(partes[2].trim(), FORMATO_HORA);
            final LocalTime horaLlegada = LocalTime.parse(partes[3].trim(), FORMATO_HORA);
            final int capacidad = Integer.parseInt(partes[4].trim());

            final String codigo = icaoOrigen + "-" + icaoDestino + "-" + partes[2].trim();
            final String id = String.format("VP%06d", secuencia++);
            vuelos.add(new VueloProgramado(id, codigo, horaSalida, horaLlegada, capacidad, origen, destino));
        }
        return vuelos;
    }

    private static List<String> leerLineas(final Path archivo) {
        try {
            return Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo de planes de vuelo: " + archivo, ex);
        }
    }
}
