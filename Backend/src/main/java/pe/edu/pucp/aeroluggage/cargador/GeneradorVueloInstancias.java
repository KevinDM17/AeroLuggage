package pe.edu.pucp.aeroluggage.cargador;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;

import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public final class GeneradorVueloInstancias {

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
        LocalDate dia = fechaInicio;
        while (!dia.isAfter(fechaFin)) {
            int secuencia = 1;
            for (final VueloProgramado programado : programados) {
                instancias.add(construirInstancia(programado, dia, secuencia++));
            }
            dia = dia.plusDays(1);
        }
        return instancias;
    }

    private static VueloInstancia construirInstancia(final VueloProgramado programado, final LocalDate dia,
                                                       final int secuencia) {
        final LocalDateTime salidaUtc = LocalDateTime.of(dia, programado.getHoraSalida());
        LocalDateTime llegadaUtc = LocalDateTime.of(dia, programado.getHoraLlegada());
        if (!llegadaUtc.isAfter(salidaUtc)) {
            llegadaUtc = llegadaUtc.plusDays(1);
        }
        final String orig = programado.getAeropuertoOrigen() != null
                ? programado.getAeropuertoOrigen().getIdAeropuerto() : "??";
        final String dest = programado.getAeropuertoDestino() != null
                ? programado.getAeropuertoDestino().getIdAeropuerto() : "??";
        final String fechaStr = dia.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        final String id = String.format("VUE-%s-%s-%s-%06d",
                orig, dest, fechaStr, secuencia);
        return new VueloInstancia(
                id,
                programado.getCodigo(),
                salidaUtc,
                llegadaUtc,
                programado.getCapacidadMaxima(),
                programado.getCapacidadMaxima(),
                programado.getAeropuertoOrigen(),
                programado.getAeropuertoDestino(),
                EstadoVuelo.PROGRAMADO);
    }
}
