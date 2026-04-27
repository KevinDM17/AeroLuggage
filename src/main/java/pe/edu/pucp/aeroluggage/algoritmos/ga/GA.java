package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;

public class GA extends Metaheuristico {
    private static final int UMBRAL_INSTANCIA_GRANDE = 1000;
    private static final int UMBRAL_INSTANCIA_GIGANTE = 5000;
    private static final int POBLACION_MAXIMA_GRANDE = 8;
    private static final int POBLACION_MAXIMA_GIGANTE = 4;
    private static final int GENERACIONES_MAXIMAS_GRANDE = 5;
    private static final int GENERACIONES_MAXIMAS_GIGANTE = 2;
    private static final int SIN_MEJORA_MAXIMO_GRANDE = 2;
    private static final int SIN_MEJORA_MAXIMO_GIGANTE = 1;

    private ParametrosGA parametros;
    private Individuo mejorGlobal;
    private Solucion mejorSolucion;
    private InstanciaProblema ultimaInstancia;
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
        ultimaInstancia = instancia;
        if (instancia.getGrafo() == null) {
            instancia.construirGrafo();
        }
        final long tInicio = System.currentTimeMillis();
        final Random random = new Random(parametros.getSemilla());
        final int tamanioPoblacionEfectivo = obtenerTamanioPoblacionEfectivo(instancia);
        final int maxGeneracionesEfectivo = obtenerMaxGeneracionesEfectivo(instancia);
        final int maxSinMejoraEfectivo = obtenerMaxSinMejoraEfectivo(instancia);

        Poblacion poblacion = inicializarPoblacion(instancia, random, tamanioPoblacionEfectivo);
        evaluarPoblacion(poblacion, instancia);
        mejorGlobal = clonarMejor(poblacion);
        mejorSolucion = mejorGlobal != null ? mejorGlobal.getSolucion() : null;
        generacionesSinMejora = 0;
        generacionesEjecutadas = 0;

        for (int gen = 0; gen < maxGeneracionesEfectivo; gen++) {
            if (generacionesSinMejora >= maxSinMejoraEfectivo) {
                break;
            }
            final Poblacion siguiente = new Poblacion(tamanioPoblacionEfectivo);
            agregarElites(poblacion, siguiente);

            while (siguiente.tamano() < tamanioPoblacionEfectivo) {
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
        if (mejorGlobal == null || mejorGlobal.getSolucion() == null || ultimaInstancia == null) {
            return;
        }
        final double costo = FuncionCosto.calcularCostoSolucion(mejorGlobal.getSolucion(), ultimaInstancia, parametros);
        final double fitness = FuncionCosto.costoAFitness(costo);
        mejorGlobal.getSolucion().setFitness(fitness);
        mejorGlobal.setFitness(fitness);
        mejorSolucion = mejorGlobal.getSolucion();
    }

    private Poblacion inicializarPoblacion(final InstanciaProblema instancia, final Random random,
                                           final int tamanioPoblacion) {
        final Poblacion poblacion = new Poblacion(tamanioPoblacion);
        final int greedy = Math.max(1, (int) Math.round(
                tamanioPoblacion * parametros.getPesoGreedySolomon()));
        for (int i = 0; i < greedy; i++) {
            final Solucion s = HeuristicaSolomon.construir(instancia, parametros, random);
            Reparador.reparar(s, instancia, parametros, random);
            poblacion.agregar(new Individuo(s));
        }
        while (poblacion.tamano() < tamanioPoblacion) {
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

    private int obtenerTamanioPoblacionEfectivo(final InstanciaProblema instancia) {
        // final int cantidadMaletas = instancia.getMaletas().size();
        // if (cantidadMaletas >= UMBRAL_INSTANCIA_GIGANTE) {
        //     return Math.min(parametros.getTamanioPoblacion(), POBLACION_MAXIMA_GIGANTE);
        // }
        // if (cantidadMaletas >= UMBRAL_INSTANCIA_GRANDE) {
        //     return Math.min(parametros.getTamanioPoblacion(), POBLACION_MAXIMA_GRANDE);
        // }
        return Math.max(1, parametros.getTamanioPoblacion());
    }

    private int obtenerMaxGeneracionesEfectivo(final InstanciaProblema instancia) {
        // final int cantidadMaletas = instancia.getMaletas().size();
        // if (cantidadMaletas >= UMBRAL_INSTANCIA_GIGANTE) {
        //     return Math.min(parametros.getMaxGeneraciones(), GENERACIONES_MAXIMAS_GIGANTE);
        // }
        // if (cantidadMaletas >= UMBRAL_INSTANCIA_GRANDE) {
        //     return Math.min(parametros.getMaxGeneraciones(), GENERACIONES_MAXIMAS_GRANDE);
        // }
        return Math.max(1, parametros.getMaxGeneraciones());
    }

    private int obtenerMaxSinMejoraEfectivo(final InstanciaProblema instancia) {
        // final int cantidadMaletas = instancia.getMaletas().size();
        // if (cantidadMaletas >= UMBRAL_INSTANCIA_GIGANTE) {
        //     return Math.min(parametros.getMaxSinMejora(), SIN_MEJORA_MAXIMO_GIGANTE);
        // }
        // if (cantidadMaletas >= UMBRAL_INSTANCIA_GRANDE) {
        //     return Math.min(parametros.getMaxSinMejora(), SIN_MEJORA_MAXIMO_GRANDE);
        // }
        return Math.max(1, parametros.getMaxSinMejora());
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
        final List<Individuo> candidatos = new ArrayList<>(k);
        Individuo mejor = null;
        for (int i = 0; i < k; i++) {
            final Individuo candidato = poblacion.get(random.nextInt(poblacion.tamano()));
            candidatos.add(candidato);
            if (mejor == null || candidato.getFitness() > mejor.getFitness()) {
                mejor = candidato;
            }
        }
        if (random.nextDouble() < parametros.getProbTorneo()) {
            return mejor;
        }
        return candidatos.get(random.nextInt(candidatos.size()));
    }
}
