package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pe.edu.pucp.aeroluggage.algorithms.Individuo;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;

public final class AlgoritmoGeneticoHibrido {
    private final InstanciaProblema instanciaProblema;
    private final ConfiguracionGenetico configuracion;
    private final Map<String, List<RutaCandidata>> rutasPorMaleta;
    private final EvaluadorFitness evaluador;
    private final Random aleatorio;
    private final OperadorSeleccion seleccion;
    private final OperadorCruce cruce;
    private final OperadorMutacion mutacion;
    private final BusquedaLocalHibrida busquedaLocal;

    public AlgoritmoGeneticoHibrido(final InstanciaProblema instanciaProblema,
        final ConfiguracionGenetico configuracion) {
        this.instanciaProblema = instanciaProblema;
        this.configuracion = configuracion == null ? ConfiguracionGenetico.porDefecto() : configuracion;
        this.rutasPorMaleta = GeneradorRutasCandidatas.generar(instanciaProblema);
        this.evaluador = new EvaluadorFitness(instanciaProblema, rutasPorMaleta, this.configuracion);
        this.aleatorio = new Random(this.configuracion.getSemilla());
        this.seleccion = new OperadorSeleccion(this.configuracion.getTamanoTorneo(), aleatorio);
        this.cruce = new OperadorCruce(this.configuracion.getTasaCruce(), aleatorio);
        this.mutacion = new OperadorMutacion(this.configuracion.getTasaMutacion(), aleatorio);
        this.busquedaLocal = new BusquedaLocalHibrida(evaluador, this.configuracion, aleatorio);
    }

    public ResultadoGenetico ejecutar() {
        final long inicio = System.currentTimeMillis();
        final long limite = inicio + configuracion.getTiempoMaximoMs();
        final List<CromosomaGenetico> poblacion = crearPoblacionInicial();
        if (poblacion.isEmpty()) {
            return new ResultadoGenetico(
                new Individuo(List.of(), Double.MAX_VALUE),
                new MetricasSolucion(0, 0, 0, 0, 0, 0, 0, 0.0),
                0,
                0L,
                List.of()
            );
        }

        evaluarPoblacion(poblacion);
        CromosomaGenetico mejorGlobal = seleccionarMejor(poblacion);
        final List<Double> historialFitness = new ArrayList<>();
        historialFitness.add(mejorGlobal.getFitness());
        int generacion = 0;

        while (generacion < configuracion.getGeneracionesMaximas() && System.currentTimeMillis() < limite) {
            final List<CromosomaGenetico> nuevaPoblacion = generarDescendencia(poblacion);
            evaluarPoblacion(nuevaPoblacion);
            aplicarBusquedaLocal(nuevaPoblacion);
            poblacion.clear();
            poblacion.addAll(nuevaPoblacion);
            final CromosomaGenetico mejorGeneracion = seleccionarMejor(poblacion);
            if (mejorGeneracion.getFitness() < mejorGlobal.getFitness()) {
                mejorGlobal = new CromosomaGenetico(mejorGeneracion);
            }
            historialFitness.add(mejorGlobal.getFitness());
            generacion++;
        }

        final MetricasSolucion metricas = evaluador.evaluar(mejorGlobal);
        final Individuo individuoFinal = new Individuo(
            mejorGlobal.convertirEnAsignaciones(rutasPorMaleta),
            metricas.getPenalizacionTotal()
        );
        return new ResultadoGenetico(
            individuoFinal,
            metricas,
            generacion,
            System.currentTimeMillis() - inicio,
            historialFitness
        );
    }

    public EvaluadorFitness getEvaluador() {
        return evaluador;
    }

    public Map<String, List<RutaCandidata>> getRutasPorMaleta() {
        return Collections.unmodifiableMap(rutasPorMaleta);
    }

    private List<CromosomaGenetico> crearPoblacionInicial() {
        final List<MaletaProcesada> maletas = instanciaProblema.getMaletasProcesadas();
        if (maletas.isEmpty()) {
            return new ArrayList<>();
        }
        final List<CromosomaGenetico> poblacion = new ArrayList<>();
        poblacion.add(construirCromosomaGreedy(maletas));
        while (poblacion.size() < configuracion.getTamanoPoblacion()) {
            poblacion.add(construirCromosomaAleatorio(maletas));
        }
        return poblacion;
    }

    private CromosomaGenetico construirCromosomaGreedy(final List<MaletaProcesada> maletas) {
        final CromosomaGenetico cromosoma = new CromosomaGenetico();
        final Map<String, Integer> cargas = new HashMap<>();
        for (final MaletaProcesada maleta : maletas) {
            final List<RutaCandidata> rutas = rutasPorMaleta.getOrDefault(maleta.getIdMaleta(), List.of());
            if (rutas.isEmpty()) {
                cromosoma.asignarRuta(maleta.getIdMaleta(), -1);
                continue;
            }
            int mejorIndice = 0;
            double mejorPuntaje = Double.MAX_VALUE;
            for (int i = 0; i < rutas.size(); i++) {
                final RutaCandidata ruta = rutas.get(i);
                double puntaje = ruta.isCumpleLlegadaADestino() ? 0.0 : configuracion.getPesoViolacionPlazo();
                for (final String idVuelo : ruta.getIdVuelos()) {
                    final int cargaActual = cargas.getOrDefault(idVuelo, 0);
                    final int capacidad = Math.max(1, capacidadVuelo(idVuelo));
                    puntaje += (cargaActual + 1.0) / capacidad * configuracion.getPesoSobrecargaCapacidad();
                }
                puntaje += ruta.longitud() * configuracion.getPesoLongitudRuta();
                if (puntaje < mejorPuntaje) {
                    mejorPuntaje = puntaje;
                    mejorIndice = i;
                }
            }
            cromosoma.asignarRuta(maleta.getIdMaleta(), mejorIndice);
            for (final String idVuelo : rutas.get(mejorIndice).getIdVuelos()) {
                cargas.merge(idVuelo, 1, Integer::sum);
            }
        }
        return cromosoma;
    }

    private CromosomaGenetico construirCromosomaAleatorio(final List<MaletaProcesada> maletas) {
        final CromosomaGenetico cromosoma = new CromosomaGenetico();
        for (final MaletaProcesada maleta : maletas) {
            final List<RutaCandidata> rutas = rutasPorMaleta.getOrDefault(maleta.getIdMaleta(), List.of());
            if (rutas.isEmpty()) {
                cromosoma.asignarRuta(maleta.getIdMaleta(), -1);
                continue;
            }
            cromosoma.asignarRuta(maleta.getIdMaleta(), aleatorio.nextInt(rutas.size()));
        }
        return cromosoma;
    }

    private int capacidadVuelo(final String idVuelo) {
        return evaluador.buscarVuelo(idVuelo) == null ? 1 : evaluador.buscarVuelo(idVuelo).getCapacidadMaxima();
    }

    private void evaluarPoblacion(final List<CromosomaGenetico> poblacion) {
        for (final CromosomaGenetico cromosoma : poblacion) {
            cromosoma.setFitness(evaluador.fitness(cromosoma));
        }
    }

    private void aplicarBusquedaLocal(final List<CromosomaGenetico> poblacion) {
        if (configuracion.getIteracionesBusquedaLocal() <= 0) {
            return;
        }
        poblacion.sort(Comparator.comparingDouble(CromosomaGenetico::getFitness));
        final int limiteMejorar = Math.min(poblacion.size(), Math.max(1, poblacion.size() / 4));
        for (int i = 0; i < limiteMejorar; i++) {
            final CromosomaGenetico mejorado = busquedaLocal.mejorar(poblacion.get(i));
            mejorado.setFitness(evaluador.fitness(mejorado));
            poblacion.set(i, mejorado);
        }
    }

    private List<CromosomaGenetico> generarDescendencia(final List<CromosomaGenetico> poblacion) {
        final List<CromosomaGenetico> hijos = new ArrayList<>();
        final CromosomaGenetico elite = seleccionarMejor(poblacion);
        hijos.add(new CromosomaGenetico(elite));
        while (hijos.size() < configuracion.getTamanoPoblacion()) {
            final CromosomaGenetico padreA = seleccion.seleccionarPorTorneo(poblacion);
            final CromosomaGenetico padreB = seleccion.seleccionarPorTorneo(poblacion);
            final CromosomaGenetico[] descendientes = cruce.cruzar(padreA, padreB);
            for (final CromosomaGenetico descendiente : descendientes) {
                mutacion.mutar(descendiente, rutasPorMaleta);
                hijos.add(descendiente);
                if (hijos.size() >= configuracion.getTamanoPoblacion()) {
                    break;
                }
            }
        }
        return hijos;
    }

    private CromosomaGenetico seleccionarMejor(final List<CromosomaGenetico> poblacion) {
        CromosomaGenetico mejor = poblacion.get(0);
        for (final CromosomaGenetico candidato : poblacion) {
            if (candidato.getFitness() < mejor.getFitness()) {
                mejor = candidato;
            }
        }
        return mejor;
    }
}
