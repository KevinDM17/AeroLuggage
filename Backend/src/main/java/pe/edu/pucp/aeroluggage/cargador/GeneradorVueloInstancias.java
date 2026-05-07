package pe.edu.pucp.aeroluggage.cargador;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public final class GeneradorVueloInstancias {

    private static final int HORAS_DIA = 24;

    private GeneradorVueloInstancias() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<VueloInstancia> generar(final List<VueloProgramado> programados,
                                                final LocalDate fechaInicio, final LocalDate fechaFin) {
        if (programados == null || programados.isEmpty()) {
            return List.of();
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("fechaFin anterior a fechaInicio");
        }
        final List<VueloInstancia> instancias = new ArrayList<>();
        int secuencia = 1;
        LocalDate dia = fechaInicio;
        while (!dia.isAfter(fechaFin)) {
            for (final VueloProgramado programado : programados) {
                instancias.add(construirInstancia(programado, dia, secuencia++));
            }
            dia = dia.plusDays(1);
        }
        return instancias;
    }

    private static VueloInstancia construirInstancia(final VueloProgramado programado, final LocalDate dia,
                                                      final int secuencia) {
        final Aeropuerto origen = programado.getAeropuertoOrigen();
        final Aeropuerto destino = programado.getAeropuertoDestino();

        final LocalDateTime salidaLocal = LocalDateTime.of(dia, programado.getHoraSalida());
        final LocalDateTime salidaUtc = salidaLocal.minusHours(origen.getHusoGMT());

        LocalDateTime llegadaLocal = LocalDateTime.of(dia, programado.getHoraLlegada());
        LocalDateTime llegadaUtc = llegadaLocal.minusHours(destino.getHusoGMT());
        if (!llegadaUtc.isAfter(salidaUtc)) {
            llegadaUtc = llegadaUtc.plusHours(HORAS_DIA);
        }
        final long horasDuracion = Duration.between(salidaUtc, llegadaUtc).toHours();
        if (horasDuracion > HORAS_DIA) {
            llegadaUtc = llegadaUtc.minusHours(HORAS_DIA);
        }

        final String id = String.format("VI%08d", secuencia);
        return new VueloInstancia(
                id,
                programado.getCodigo(),
                salidaUtc,
                llegadaUtc,
                programado.getCapacidadMaxima(),
                programado.getCapacidadMaxima(),
                origen,
                destino,
                EstadoVuelo.PROGRAMADO);
    }
}
