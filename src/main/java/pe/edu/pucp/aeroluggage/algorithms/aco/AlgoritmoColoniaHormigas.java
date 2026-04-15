package pe.edu.pucp.aeroluggage.algorithms.aco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pe.edu.pucp.aeroluggage.algorithms.Asignacion;
import pe.edu.pucp.aeroluggage.algorithms.Individuo;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public class AlgoritmoColoniaHormigas {
    private static final double FITNESS_BASE = 1.0;
    private static final double HEURISTICA_BASE = 1.0;
    private static final double PENALIZACION_SIN_VUELO = 1_000.0;

    private final InstanciaProblema instanciaProblema;
    private final int numHormigas;
    private final int numIteraciones;
    private final double tasaEvaporacion;
    private final double alpha;
    private final double beta;
    private final double feromonaInicial;
    private final double depositoFeromona;
    private final Map<String, Map<String, Double>> feromonas;
    private final Random random;

    public AlgoritmoColoniaHormigas(final InstanciaProblema instanciaProblema, final int numHormigas,
        final int numIteraciones, final double tasaEvaporacion, final double alpha, final double beta,
        final double feromonaInicial, final double depositoFeromona) {
        this.instanciaProblema = instanciaProblema;
        this.numHormigas = numHormigas;
        this.numIteraciones = numIteraciones;
        this.tasaEvaporacion = tasaEvaporacion;
        this.alpha = alpha;
        this.beta = beta;
        this.feromonaInicial = feromonaInicial;
        this.depositoFeromona = depositoFeromona;
        this.feromonas = new HashMap<>();
        this.random = new Random();
        inicializarFeromonas();
    }

    public Individuo ejecutar() {
        Individuo mejorIndividuo = null;

        for (int iteracion = 0; iteracion < numIteraciones; iteracion++) {
            final List<Individuo> individuos = construirIndividuos();
            mejorIndividuo = seleccionarMejor(mejorIndividuo, individuos);
            actualizarFeromonas(individuos);
        }

        return mejorIndividuo;
    }

    public Map<String, Map<String, Double>> getFeromonas() {
        final Map<String, Map<String, Double>> copiaFeromonas = new HashMap<>();
        for (final Map.Entry<String, Map<String, Double>> entradaFeromona : feromonas.entrySet()) {
            copiaFeromonas.put(entradaFeromona.getKey(), new HashMap<>(entradaFeromona.getValue()));
        }
        return copiaFeromonas;
    }

    private void inicializarFeromonas() {
        final List<MaletaProcesada> maletasProcesadas = instanciaProblema.getMaletasProcesadas();
        final List<Vuelo> vuelos = instanciaProblema.getVuelos();

        for (final MaletaProcesada maletaProcesada : maletasProcesadas) {
            final Map<String, Double> feromonasPorVuelo = new HashMap<>();
            for (final Vuelo vuelo : vuelos) {
                feromonasPorVuelo.put(vuelo.getIdVuelo(), feromonaInicial);
            }
            feromonas.put(maletaProcesada.getIdMaleta(), feromonasPorVuelo);
        }
    }

    private List<Individuo> construirIndividuos() {
        final List<Individuo> individuos = new ArrayList<>();
        for (int hormiga = 0; hormiga < numHormigas; hormiga++) {
            individuos.add(construirIndividuo());
        }
        return individuos;
    }

    private Individuo construirIndividuo() {
        final List<Asignacion> asignaciones = new ArrayList<>();
        for (final MaletaProcesada maletaProcesada : instanciaProblema.getMaletasProcesadas()) {
            final Vuelo vuelo = seleccionarVuelo(maletaProcesada);
            if (vuelo != null) {
                asignaciones.add(new Asignacion(maletaProcesada.getIdMaleta(), vuelo.getIdVuelo()));
            }
        }

        return new Individuo(asignaciones, calcularFitness(asignaciones));
    }

    private Vuelo seleccionarVuelo(final MaletaProcesada maletaProcesada) {
        final List<Vuelo> vuelos = instanciaProblema.getVuelos();
        if (vuelos.isEmpty()) {
            return null;
        }

        final List<Double> probabilidades = calcularProbabilidades(maletaProcesada, vuelos);
        final double seleccion = random.nextDouble();
        double acumulado = 0.0;

        for (int indice = 0; indice < vuelos.size(); indice++) {
            acumulado += probabilidades.get(indice);
            if (seleccion <= acumulado) {
                return vuelos.get(indice);
            }
        }

        return vuelos.get(vuelos.size() - 1);
    }

    private List<Double> calcularProbabilidades(final MaletaProcesada maletaProcesada, final List<Vuelo> vuelos) {
        final List<Double> pesos = new ArrayList<>();
        double total = 0.0;

        for (final Vuelo vuelo : vuelos) {
            final double feromona = obtenerFeromona(maletaProcesada.getIdMaleta(), vuelo.getIdVuelo());
            final double heuristica = calcularHeuristica(maletaProcesada, vuelo);
            final double peso = Math.pow(feromona, alpha) * Math.pow(heuristica, beta);

            pesos.add(peso);
            total += peso;
        }

        return normalizarProbabilidades(pesos, total);
    }

    private List<Double> normalizarProbabilidades(final List<Double> pesos, final double total) {
        final List<Double> probabilidades = new ArrayList<>();
        if (total <= 0.0) {
            final double probabilidadUniforme = 1.0 / pesos.size();
            for (int indice = 0; indice < pesos.size(); indice++) {
                probabilidades.add(probabilidadUniforme);
            }
            return probabilidades;
        }

        for (final double peso : pesos) {
            probabilidades.add(peso / total);
        }
        return probabilidades;
    }

    private double calcularHeuristica(final MaletaProcesada maletaProcesada, final Vuelo vuelo) {
        final boolean vueloDisponible = vuelo.getCapacidadDisponible() > 0;
        if (!vueloDisponible) {
            return 0.0;
        }

        return HEURISTICA_BASE / Math.max(FITNESS_BASE, maletaProcesada.getPlazoMaximoDias());
    }

    private double calcularFitness(final List<Asignacion> asignaciones) {
        final int cantidadMaletas = instanciaProblema.getMaletasProcesadas().size();
        final int cantidadSinAsignar = cantidadMaletas - asignaciones.size();

        return asignaciones.size() + (cantidadSinAsignar * PENALIZACION_SIN_VUELO);
    }

    private Individuo seleccionarMejor(final Individuo mejorIndividuo, final List<Individuo> individuos) {
        Individuo mejorActual = mejorIndividuo;

        for (final Individuo individuo : individuos) {
            final boolean mejoraSolucion = mejorActual == null || individuo.getFitness() < mejorActual.getFitness();
            if (mejoraSolucion) {
                mejorActual = individuo;
            }
        }

        return mejorActual;
    }

    private void actualizarFeromonas(final List<Individuo> individuos) {
        evaporarFeromonas();

        for (final Individuo individuo : individuos) {
            final double deposito = depositoFeromona / Math.max(FITNESS_BASE, individuo.getFitness());
            for (final Asignacion asignacion : individuo.getAsignaciones()) {
                final double feromonaActual = obtenerFeromona(asignacion.getIdMaleta(), asignacion.getIdVuelo());
                feromonas.get(asignacion.getIdMaleta()).put(asignacion.getIdVuelo(), feromonaActual + deposito);
            }
        }
    }

    private void evaporarFeromonas() {
        final double factorEvaporacion = Math.max(0.0, 1.0 - tasaEvaporacion);

        for (final Map<String, Double> feromonasPorVuelo : feromonas.values()) {
            for (final Map.Entry<String, Double> entradaFeromona : feromonasPorVuelo.entrySet()) {
                entradaFeromona.setValue(entradaFeromona.getValue() * factorEvaporacion);
            }
        }
    }

    private double obtenerFeromona(final String idMaleta, final String idVuelo) {
        final Map<String, Double> feromonasPorVuelo = feromonas.get(idMaleta);
        if (feromonasPorVuelo == null) {
            return feromonaInicial;
        }

        return feromonasPorVuelo.getOrDefault(idVuelo, feromonaInicial);
    }
}
