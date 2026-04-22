package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Poblacion {

    private final List<Individuo> individuos;

    public Poblacion() {
        this.individuos = new ArrayList<>();
    }

    public Poblacion(final int capacidadInicial) {
        this.individuos = new ArrayList<>(Math.max(0, capacidadInicial));
    }

    public Poblacion(final List<Individuo> individuos) {
        this.individuos = individuos != null ? new ArrayList<>(individuos) : new ArrayList<>();
    }

    public void agregar(final Individuo individuo) {
        if (individuo != null) {
            individuos.add(individuo);
        }
    }

    public void agregarTodos(final List<Individuo> otros) {
        if (otros == null) {
            return;
        }
        for (final Individuo ind : otros) {
            agregar(ind);
        }
    }

    public Individuo get(final int indice) {
        return individuos.get(indice);
    }

    public int tamano() {
        return individuos.size();
    }

    public boolean estaVacia() {
        return individuos.isEmpty();
    }

    public List<Individuo> getIndividuos() {
        return individuos;
    }

    public void ordenarPorFitnessDesc() {
        individuos.sort(Comparator.comparingDouble(Individuo::getFitness).reversed());
    }

    public Individuo mejor() {
        if (individuos.isEmpty()) {
            return null;
        }
        Individuo mejor = individuos.get(0);
        for (int i = 1; i < individuos.size(); i++) {
            final Individuo candidato = individuos.get(i);
            if (candidato.getFitness() > mejor.getFitness()) {
                mejor = candidato;
            }
        }
        return mejor;
    }

    public double fitnessPromedio() {
        if (individuos.isEmpty()) {
            return 0.0;
        }
        double suma = 0.0;
        for (final Individuo ind : individuos) {
            suma += ind.getFitness();
        }
        return suma / individuos.size();
    }

    public List<Individuo> topK(final int k) {
        if (k <= 0 || individuos.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Individuo> copia = new ArrayList<>(individuos);
        copia.sort(Comparator.comparingDouble(Individuo::getFitness).reversed());
        return copia.subList(0, Math.min(k, copia.size()));
    }

    public void limpiar() {
        individuos.clear();
    }
}
