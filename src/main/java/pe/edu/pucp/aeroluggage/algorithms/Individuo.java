package pe.edu.pucp.aeroluggage.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Individuo {
    private final List<Asignacion> asignaciones;
    private final double fitness;

    public Individuo(final List<Asignacion> asignaciones, final double fitness) {
        this.asignaciones = copyAsignaciones(asignaciones);
        this.fitness = fitness;
    }

    public List<Asignacion> getAsignaciones() {
        return copyAsignaciones(asignaciones);
    }

    public double getFitness() {
        return fitness;
    }

    private static List<Asignacion> copyAsignaciones(final List<Asignacion> asignaciones) {
        if (asignaciones == null || asignaciones.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Asignacion> copiaAsignaciones = new ArrayList<>();
        for (final Asignacion asignacion : asignaciones) {
            copiaAsignaciones.add(new Asignacion(asignacion));
        }
        return copiaAsignaciones;
    }
}
