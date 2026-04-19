package pe.edu.pucp.aeroluggage.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public final class VueloLoader {
    private static final int CAMPOS_ESPERADOS = 5;

    private VueloLoader() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<PlanDeVuelo> cargarPlanes(final String recurso) {
        final InputStream stream = VueloLoader.class.getClassLoader().getResourceAsStream(recurso);
        if (stream == null) {
            throw new IllegalArgumentException("Recurso no encontrado: " + recurso);
        }
        return leerPlanes(stream);
    }

    public static List<PlanDeVuelo> cargarPlanesDesdeArchivo(final Path ruta) {
        if (ruta == null || !Files.exists(ruta)) {
            throw new IllegalArgumentException("Archivo no encontrado: " + ruta);
        }
        try {
            return leerPlanes(Files.newInputStream(ruta));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Vuelo> expandirVuelos(final List<PlanDeVuelo> planes, final LocalDate fechaInicio,
        final int cantidadDias, final Map<String, Integer> husosHorarios) {
        if (planes == null || planes.isEmpty() || cantidadDias <= 0) {
            return new ArrayList<>();
        }
        final List<Vuelo> vuelos = new ArrayList<>();
        int secuencia = 1;
        for (int dia = 0; dia < cantidadDias; dia++) {
            final LocalDate fechaBase = fechaInicio.plusDays(dia);
            for (final PlanDeVuelo plan : planes) {
                final int husoOrigen = husosHorarios.getOrDefault(plan.getOrigen(), 0);
                final int husoDestino = husosHorarios.getOrDefault(plan.getDestino(), 0);
                final LocalDateTime salidaLocal = LocalDateTime.of(fechaBase, plan.getHoraSalida());
                LocalDateTime llegadaLocal = LocalDateTime.of(fechaBase, plan.getHoraLlegada());
                final int minutosViaje = minutosTotalesViaje(
                    plan.getHoraSalida(),
                    plan.getHoraLlegada(),
                    husoOrigen,
                    husoDestino
                );
                llegadaLocal = salidaLocal.plusMinutes(minutosViaje + diferenciaZonaEnMinutos(husoOrigen, husoDestino));
                final Date fechaSalidaUtc = Date.from(salidaLocal.minusHours(husoOrigen).toInstant(ZoneOffset.UTC));
                final Date fechaLlegadaUtc = Date.from(llegadaLocal.minusHours(husoDestino).toInstant(ZoneOffset.UTC));
                final String idVuelo = "VUE-" + String.format("%07d", secuencia);
                final String codigo = plan.getOrigen() + "-" + plan.getDestino() + "-"
                    + fechaBase.toString() + "-" + plan.getHoraSalida();
                vuelos.add(new Vuelo(
                    idVuelo,
                    codigo,
                    fechaSalidaUtc,
                    fechaLlegadaUtc,
                    plan.getCapacidad(),
                    plan.getCapacidad(),
                    "PROGRAMADO"
                ));
                secuencia++;
            }
        }
        return vuelos;
    }

    private static int minutosTotalesViaje(final LocalTime horaSalida, final LocalTime horaLlegada,
        final int husoOrigen, final int husoDestino) {
        final int minutosSalida = horaSalida.toSecondOfDay() / 60;
        int minutosLlegada = horaLlegada.toSecondOfDay() / 60;
        final int delta = (husoDestino - husoOrigen) * 60;
        if (minutosLlegada <= minutosSalida) {
            minutosLlegada += 24 * 60;
        }
        final int duracion = minutosLlegada - minutosSalida - delta;
        return duracion > 0 ? duracion : duracion + 24 * 60;
    }

    private static int diferenciaZonaEnMinutos(final int husoOrigen, final int husoDestino) {
        return (husoDestino - husoOrigen) * 60;
    }

    private static List<PlanDeVuelo> leerPlanes(final InputStream stream) {
        final List<PlanDeVuelo> planes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                final String lineaLimpia = linea.trim();
                if (lineaLimpia.isEmpty()) {
                    continue;
                }
                final String[] campos = lineaLimpia.split("-");
                if (campos.length < CAMPOS_ESPERADOS) {
                    continue;
                }
                final String origen = campos[0].trim();
                final String destino = campos[1].trim();
                final LocalTime horaSalida = LocalTime.parse(campos[2].trim());
                final LocalTime horaLlegada = LocalTime.parse(campos[3].trim());
                final int capacidad = Integer.parseInt(campos[4].trim());
                planes.add(new PlanDeVuelo(origen, destino, horaSalida, horaLlegada, capacidad));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return planes;
    }
}
