package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;

public class GA extends Metaheuristico {

    private ParametrosGA parametros;
    private Individuo mejorGlobal;
    private Solucion mejorSolucion;
    private int generacionesEjecutadas;
    private int generacionesSinMejora;
    private long tiempoEjecucionMs;

    public GA() {
        this.parametros = ParametrosGA.pordefecto();
    }

    public GA(final ParametrosGA parametros) {
        this.parametros = parametros != null ? parametros : ParametrosGA.pordefecto();
    }

    public ParametrosGA getParametros() {
        return parametros;
    }

    public void setParametros(final ParametrosGA parametros) {
        this.parametros = parametros != null ? parametros : ParametrosGA.pordefecto();
    }

    public Solucion getMejorSolucion() {
        return mejorSolucion;
    }

    public Individuo getMejorGlobal() {
        return mejorGlobal;
    }

    public int getGeneracionesEjecutadas() {
        return generacionesEjecutadas;
    }

    public long getTiempoEjecucionMs() {
        return tiempoEjecucionMs;
    }

    @Override
    public void ejecutar(final InstanciaProblema instancia) {
        if (instancia == null) {
            return;
        }
        if (instancia.getGrafo() == null) {
            instancia.construirGrafo();
        }
        final long tInicio = System.currentTimeMillis();
        final Random random = new Random(parametros.getSemilla());

        Poblacion poblacion = inicializarPoblacion(instancia, random);
        evaluarPoblacion(poblacion, instancia);
        mejorGlobal = clonarMejor(poblacion);
        mejorSolucion = mejorGlobal != null ? mejorGlobal.getSolucion() : null;
        generacionesSinMejora = 0;
        generacionesEjecutadas = 0;

        for (int gen = 0; gen < parametros.getMaxGeneraciones(); gen++) {
            if (System.currentTimeMillis() - tInicio > parametros.getTiempoMaximoMs()) {
                break;
            }
            if (generacionesSinMejora >= parametros.getMaxSinMejora()) {
                break;
            }
            final Poblacion siguiente = new Poblacion(parametros.getTamanioPoblacion());
            agregarElites(poblacion, siguiente);

            while (siguiente.tamano() < parametros.getTamanioPoblacion()) {
                final Individuo padre1 = seleccionTorneo(poblacion, random);
                final Individuo padre2 = seleccionTorneo(poblacion, random);
                Solucion hijoSolucion;
                if (random.nextDouble() < parametros.getProbCruce()) {
                    hijoSolucion = OperadorCruce.cruzarSoluciones(
                            padre1.getSolucion(), padre2.getSolucion(), random);
                } else {
                    hijoSolucion = padre1.getSolucion().clonarProfundo();
                }
                if (random.nextDouble() < parametros.getProbMutacion()) {
                    OperadorMutacion.mutar(hijoSolucion, instancia, parametros, random);
                }
                Reparador.reparar(hijoSolucion, instancia, parametros, random);

                final Individuo hijo = new Individuo(hijoSolucion);
                evaluarIndividuo(hijo, instancia);
                siguiente.agregar(hijo);
            }

            poblacion = siguiente;
            final Individuo mejorActual = poblacion.mejor();
            if (mejorActual != null && (mejorGlobal == null || mejorActual.getFitness() > mejorGlobal.getFitness())) {
                mejorGlobal = mejorActual.clonarProfundo();
                mejorSolucion = mejorGlobal.getSolucion();
                generacionesSinMejora = 0;
            } else {
                generacionesSinMejora++;
            }
            generacionesEjecutadas = gen + 1;
        }

        tiempoEjecucionMs = System.currentTimeMillis() - tInicio;
    }

    @Override
    public void evaluar() {
        if (mejorGlobal != null && mejorGlobal.getSolucion() != null) {
            mejorGlobal.getSolucion().calcularMetricas();
        }
    }

    private Poblacion inicializarPoblacion(final InstanciaProblema instancia, final Random random) {
        final Poblacion poblacion = new Poblacion(parametros.getTamanioPoblacion());
        final int greedy = Math.max(1, (int) Math.round(
                parametros.getTamanioPoblacion() * parametros.getPesoGreedySolomon()));
        for (int i = 0; i < greedy; i++) {
            final Solucion s = HeuristicaSolomon.construir(instancia, parametros, random);
            Reparador.reparar(s, instancia, parametros, random);
            poblacion.agregar(new Individuo(s));
        }
        while (poblacion.tamano() < parametros.getTamanioPoblacion()) {
            final Solucion s = HeuristicaSolomon.construirAleatorizado(instancia, parametros, random);
            Reparador.reparar(s, instancia, parametros, random);
            poblacion.agregar(new Individuo(s));
        }
        return poblacion;
    }

    private void evaluarPoblacion(final Poblacion poblacion, final InstanciaProblema instancia) {
        for (final Individuo ind : poblacion.getIndividuos()) {
            evaluarIndividuo(ind, instancia);
        }
    }

    private void evaluarIndividuo(final Individuo individuo, final InstanciaProblema instancia) {
        final Solucion solucion = individuo.getSolucion();
        if (solucion == null) {
            individuo.setFitness(0.0);
            return;
        }
        final double costo = FuncionCosto.calcularCostoSolucion(solucion, instancia, parametros);
        final double fitness = FuncionCosto.costoAFitness(costo);
        solucion.setFitness(fitness);
        individuo.setFitness(fitness);
    }

    private Individuo clonarMejor(final Poblacion poblacion) {
        final Individuo mejor = poblacion.mejor();
        return mejor != null ? mejor.clonarProfundo() : null;
    }

    private void agregarElites(final Poblacion origen, final Poblacion destino) {
        final List<Individuo> elites = new ArrayList<>(origen.topK(parametros.getElites()));
        for (final Individuo elite : elites) {
            destino.agregar(elite.clonarProfundo());
        }
    }

    private Individuo seleccionTorneo(final Poblacion poblacion, final Random random) {
        final int k = Math.max(2, parametros.getTorneoK());
        Individuo mejor = null;
        for (int i = 0; i < k; i++) {
            final Individuo candidato = poblacion.get(random.nextInt(poblacion.tamano()));
            if (mejor == null || candidato.getFitness() > mejor.getFitness()) {
                mejor = candidato;
            }
        }
        return mejor;
    }
}
