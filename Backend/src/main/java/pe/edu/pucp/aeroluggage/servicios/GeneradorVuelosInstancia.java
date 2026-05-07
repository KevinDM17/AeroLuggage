package pe.edu.pucp.aeroluggage.servicios;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public final class GeneradorVuelosInstancia {
    private GeneradorVuelosInstancia() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static ArrayList<VueloInstancia> generar(final ArrayList<VueloProgramado> vuelosProgramados,
                                                    final LocalDate fechaInicio,
                                                    final int dias) {
        final ArrayList<VueloInstancia> instancias = new ArrayList<>();
        if (vuelosProgramados == null || vuelosProgramados.isEmpty() || fechaInicio == null || dias <= 0) {
            return instancias;
        }

        for (int diaOffset = 0; diaOffset < dias; diaOffset++) {
            final LocalDate fechaOperacion = fechaInicio.plusDays(diaOffset);
            for (final VueloProgramado vueloProgramado : vuelosProgramados) {
                final VueloInstancia instancia = crearInstancia(vueloProgramado, fechaOperacion);
                if (instancia == null) {
                    continue;
                }
                instancias.add(instancia);
            }
        }
        return instancias;
    }

    private static VueloInstancia crearInstancia(final VueloProgramado vueloProgramado,
                                                 final LocalDate fechaOperacion) {
        final boolean vueloInvalido = vueloProgramado == null
                || vueloProgramado.getIdVueloProgramado() == null
                || vueloProgramado.getHoraSalida() == null
                || vueloProgramado.getHoraLlegada() == null;
        if (vueloInvalido) {
            return null;
        }

        final LocalDateTime salida = LocalDateTime.of(fechaOperacion, vueloProgramado.getHoraSalida());
        LocalDateTime llegada = LocalDateTime.of(fechaOperacion, vueloProgramado.getHoraLlegada());
        if (!llegada.isAfter(salida)) {
            llegada = llegada.plusDays(1);
        }

        final String idInstancia = vueloProgramado.getIdVueloProgramado() + "-" + fechaOperacion;
        final int capacidad = Math.max(0, vueloProgramado.getCapacidadBase());
        return new VueloInstancia(
                idInstancia,
                vueloProgramado,
                fechaOperacion,
                salida,
                llegada,
                capacidad,
                capacidad,
                EstadoVuelo.PROGRAMADO
        );
    }
}
