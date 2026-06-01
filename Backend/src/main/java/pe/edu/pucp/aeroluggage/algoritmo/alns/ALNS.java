package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;

public class ALNS extends Metaheuristico {
    private final ParametrosALNS parametros;
    private Solucion mejorSolucion;
    private Solucion solucionActual;
    private InstanciaProblema ultimaInstancia;
    private long tiempoEjecucionMs;
    private int iteracionesEjecutadas;

    public ALNS() {
        this(ParametrosALNS.porDefecto());
    }

    public ALNS(final ParametrosALNS parametros) {
        this.parametros = parametros == null ? ParametrosALNS.porDefecto() : parametros;
    }

    public ParametrosALNS getParametros() {
        return parametros;
    }

    public Solucion getMejorSolucion() {
        return mejorSolucion;
    }

    public Solucion getSolucionActual() {
        return solucionActual;
    }

    public long getTiempoEjecucionMs() {
        return tiempoEjecucionMs;
    }

    public int getIteracionesEjecutadas() {
        return iteracionesEjecutadas;
    }

    @Override
    public void ejecutar(final InstanciaProblema instancia) {
        if (instancia == null) {
            return;
        }
        this.ultimaInstancia = instancia;
        instancia.setMinutosConexion(parametros.getMinutosConexion());
        instancia.setTiempoRecojo(parametros.getTiempoRecojo());
        if (instancia.getFechaEvaluacion() == null) {
            instancia.setFechaEvaluacion(obtenerFechaEvaluacion(instancia));
        }
        if (instancia.getGrafo() == null) {
            instancia.construirGrafo();
        }
        final long inicio = System.currentTimeMillis();
        final Random random = new Random(parametros.getSemilla());
        final ALNSAdaptativo destructores = new ALNSAdaptativo(List.of(
                ALNSDestruidor.OPERADOR_WORST,
                ALNSDestruidor.OPERADOR_CRITICAL
        ));
        final ALNSAdaptativo reparadores = new ALNSAdaptativo(List.of(
                ALNSReparador.OPERADOR_GREEDY,
                ALNSReparador.OPERADOR_REGRET_2
        ));

        final Solucion inicial = ALNSInicializador.construir(instancia, parametros);
        ALNSEstado actual = new ALNSEstado(instancia, inicial);
        ALNSFitness.Resultado fitnessActual = ALNSFitness.evaluar(actual, parametros);
        ALNSEstado mejorEstado = actual.clonar();
        ALNSFitness.Resultado mejorFitness = fitnessActual;
        double temperatura = Math.max(0.0001D, parametros.getTemperaturaInicial());
        int sinMejora = 0;
        iteracionesEjecutadas = 0;

        for (int iteracion = 1; iteracion <= Math.max(1, parametros.getMaxIteraciones()); iteracion++) {
            if (sinMejora >= Math.max(1, parametros.getMaxIteracionesSinMejora())) {
                break;
            }
            if (System.currentTimeMillis() - inicio >= parametros.getTiempoMaximoMs()) {
                break;
            }

            final int q = elegirQ(random);
            final String operadorDestruccion = destructores.seleccionar(random);
            final String operadorReparacion = reparadores.seleccionar(random);
            final ALNSEstado candidato = actual.clonar();
            final List<Maleta> removidas = new ArrayList<>(ALNSDestruidor.destruir(
                    candidato, operadorDestruccion, q, parametros, random));
            for (final Maleta maleta : candidato.getMaletasSinRuta()) {
                if (!removidas.contains(maleta)) {
                    removidas.add(maleta);
                }
            }
            ALNSReparador.reparar(candidato, removidas, operadorReparacion, parametros, random);
            final ALNSFitness.Resultado fitnessCandidato = ALNSFitness.evaluar(candidato, parametros);

            final boolean esNuevaMejorGlobal = fitnessCandidato.fitness() < mejorFitness.fitness();
            final boolean mejoraActual = fitnessCandidato.fitness() < fitnessActual.fitness();
            final boolean aceptada = ALNSAceptacion.aceptar(
                    fitnessActual.fitness(),
                    fitnessCandidato.fitness(),
                    temperatura,
                    random
            );

            final double puntaje = recompensa(esNuevaMejorGlobal, mejoraActual, aceptada);
            destructores.registrarUso(operadorDestruccion, puntaje);
            reparadores.registrarUso(operadorReparacion, puntaje);

            if (aceptada) {
                actual = candidato;
                fitnessActual = fitnessCandidato;
            }
            if (esNuevaMejorGlobal) {
                mejorEstado = candidato.clonar();
                mejorFitness = fitnessCandidato;
                sinMejora = 0;
            } else {
                sinMejora++;
            }

            temperatura *= parametros.getFactorEnfriamiento();
            if (iteracion % Math.max(1, parametros.getSegmentoIteraciones()) == 0) {
                destructores.actualizarPesos(parametros);
                reparadores.actualizarPesos(parametros);
            }
            iteracionesEjecutadas = iteracion;
        }

        mejorSolucion = mejorEstado.getSolucionActual().clonarProfundo();
        solucionActual = actual.getSolucionActual().clonarProfundo();
        tiempoEjecucionMs = System.currentTimeMillis() - inicio;
    }

    @Override
    public void evaluar() {
        if (ultimaInstancia == null || mejorSolucion == null) {
            return;
        }
        final ALNSEstado estado = new ALNSEstado(ultimaInstancia, mejorSolucion);
        ALNSFitness.evaluar(estado, parametros);
        mejorSolucion = estado.getSolucionActual();
    }

    private int elegirQ(final Random random) {
        final int minimo = Math.max(1, parametros.getQMin());
        final int maximo = Math.max(minimo, parametros.getQMax());
        return minimo + random.nextInt(maximo - minimo + 1);
    }

    private double recompensa(final boolean nuevaMejorGlobal,
                              final boolean mejoraActual,
                              final boolean aceptada) {
        if (nuevaMejorGlobal) {
            return parametros.getSigma1();
        }
        if (mejoraActual) {
            return parametros.getSigma2();
        }
        if (aceptada) {
            return parametros.getSigma3();
        }
        return parametros.getSigma4();
    }

    private static java.time.LocalDateTime obtenerFechaEvaluacion(final InstanciaProblema instancia) {
        java.time.LocalDateTime minima = null;
        for (final var maleta : instancia.getMaletas()) {
            if (maleta == null || maleta.getPedido() == null || maleta.getPedido().getFechaRegistro() == null) {
                continue;
            }
            final var fecha = maleta.getPedido().getFechaRegistro();
            if (minima == null || fecha.isBefore(minima)) {
                minima = fecha;
            }
        }
        return minima;
    }
}
