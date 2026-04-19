package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public final class BusquedaLocalHibrida {
    private final EvaluadorFitness evaluador;
    private final ConfiguracionGenetico configuracion;
    private final Random aleatorio;

    public BusquedaLocalHibrida(final EvaluadorFitness evaluador, final ConfiguracionGenetico configuracion,
        final Random aleatorio) {
        this.evaluador = evaluador;
        this.configuracion = configuracion;
        this.aleatorio = aleatorio;
    }

    public CromosomaGenetico mejorar(final CromosomaGenetico cromosoma) {
        if (cromosoma == null || configuracion.getIteracionesBusquedaLocal() <= 0) {
            return cromosoma;
        }
        CromosomaGenetico actual = new CromosomaGenetico(cromosoma);
        MetricasSolucion metricas = evaluador.evaluar(actual);
        double fitnessActual = metricas.getPenalizacionTotal();
        actual.setFitness(fitnessActual);

        for (int iteracion = 0; iteracion < configuracion.getIteracionesBusquedaLocal(); iteracion++) {
            final List<String> candidatos = maletasAMover(actual, metricas);
            if (candidatos.isEmpty()) {
                break;
            }
            final String idMaleta = candidatos.get(aleatorio.nextInt(candidatos.size()));
            final List<RutaCandidata> rutas = evaluador.getRutasPorMaleta()
                .getOrDefault(idMaleta, Collections.emptyList());
            if (rutas.isEmpty()) {
                continue;
            }
            final int indiceAnterior = actual.obtenerIndiceRuta(idMaleta) == null
                ? 0
                : actual.obtenerIndiceRuta(idMaleta);
            final int indicePropuesto = seleccionarRutaMenosCongestionada(rutas, actual, idMaleta);
            if (indicePropuesto == indiceAnterior) {
                continue;
            }
            final CromosomaGenetico candidato = new CromosomaGenetico(actual);
            candidato.asignarRuta(idMaleta, indicePropuesto);
            final MetricasSolucion metricasCandidato = evaluador.evaluar(candidato);
            if (metricasCandidato.getPenalizacionTotal() < fitnessActual) {
                actual = candidato;
                fitnessActual = metricasCandidato.getPenalizacionTotal();
                metricas = metricasCandidato;
                actual.setFitness(fitnessActual);
            }
        }
        return actual;
    }

    private List<String> maletasAMover(final CromosomaGenetico cromosoma, final MetricasSolucion metricas) {
        final Map<String, Integer> cargaPorVuelo = calcularCargaPorVuelo(cromosoma);
        final List<String> afectadas = new ArrayList<>();
        for (final Map.Entry<String, Integer> entrada : cromosoma.getSeleccionRutaPorMaleta().entrySet()) {
            final String idMaleta = entrada.getKey();
            final List<RutaCandidata> rutas = evaluador.getRutasPorMaleta()
                .getOrDefault(idMaleta, Collections.emptyList());
            if (rutas.isEmpty()) {
                continue;
            }
            final int indiceActual = entrada.getValue() == null ? -1 : entrada.getValue();
            if (indiceActual < 0 || indiceActual >= rutas.size()) {
                afectadas.add(idMaleta);
                continue;
            }
            final RutaCandidata rutaActual = rutas.get(indiceActual);
            if (!rutaActual.isCumpleLlegadaADestino()) {
                afectadas.add(idMaleta);
                continue;
            }
            for (final String idVuelo : rutaActual.getIdVuelos()) {
                final Vuelo vuelo = evaluador.buscarVuelo(idVuelo);
                if (vuelo != null && cargaPorVuelo.getOrDefault(idVuelo, 0) > vuelo.getCapacidadMaxima()) {
                    afectadas.add(idMaleta);
                    break;
                }
            }
        }
        if (afectadas.isEmpty() && metricas.getVuelosSobrecargados() == 0) {
            return Collections.emptyList();
        }
        if (afectadas.isEmpty()) {
            return new ArrayList<>(cromosoma.getSeleccionRutaPorMaleta().keySet());
        }
        return afectadas;
    }

    private Map<String, Integer> calcularCargaPorVuelo(final CromosomaGenetico cromosoma) {
        final Map<String, Integer> cargas = new HashMap<>();
        for (final Map.Entry<String, Integer> entrada : cromosoma.getSeleccionRutaPorMaleta().entrySet()) {
            final List<RutaCandidata> rutas = evaluador.getRutasPorMaleta()
                .getOrDefault(entrada.getKey(), Collections.emptyList());
            if (rutas.isEmpty()) {
                continue;
            }
            final int indice = entrada.getValue() == null ? -1 : entrada.getValue();
            if (indice < 0 || indice >= rutas.size()) {
                continue;
            }
            for (final String idVuelo : rutas.get(indice).getIdVuelos()) {
                cargas.merge(idVuelo, 1, Integer::sum);
            }
        }
        return cargas;
    }

    private int seleccionarRutaMenosCongestionada(final List<RutaCandidata> rutas,
        final CromosomaGenetico cromosoma, final String idMaleta) {
        final Map<String, Integer> cargas = calcularCargaPorVuelo(cromosoma);
        final Integer indiceActual = cromosoma.obtenerIndiceRuta(idMaleta);
        int mejorIndice = indiceActual == null ? 0 : indiceActual;
        double mejorPuntaje = Double.MAX_VALUE;
        for (int i = 0; i < rutas.size(); i++) {
            final RutaCandidata ruta = rutas.get(i);
            double puntaje = ruta.isCumpleLlegadaADestino() ? 0.0 : configuracion.getPesoViolacionPlazo();
            for (final String idVuelo : ruta.getIdVuelos()) {
                final Vuelo vuelo = evaluador.buscarVuelo(idVuelo);
                if (vuelo == null) {
                    continue;
                }
                final int cargaActual = cargas.getOrDefault(idVuelo, 0);
                final double ratio = cargaActual / (double) Math.max(1, vuelo.getCapacidadMaxima());
                puntaje += ratio * configuracion.getPesoSobrecargaCapacidad();
            }
            puntaje += configuracion.getPesoLongitudRuta() * ruta.longitud();
            if (puntaje < mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejorIndice = i;
            }
        }
        return mejorIndice;
    }
}
