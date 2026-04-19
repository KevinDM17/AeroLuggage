package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public final class EvaluadorFitness {
    private final ConfiguracionGenetico configuracion;
    private final Map<String, MaletaProcesada> indiceMaletas;
    private final Map<String, Vuelo> indiceVuelos;
    private final Map<String, List<RutaCandidata>> rutasPorMaleta;

    public EvaluadorFitness(final InstanciaProblema instanciaProblema,
        final Map<String, List<RutaCandidata>> rutasPorMaleta, final ConfiguracionGenetico configuracion) {
        this.configuracion = configuracion;
        this.indiceMaletas = new HashMap<>();
        this.indiceVuelos = new HashMap<>();
        this.rutasPorMaleta = rutasPorMaleta;
        for (final MaletaProcesada maleta : instanciaProblema.getMaletasProcesadas()) {
            indiceMaletas.put(maleta.getIdMaleta(), maleta);
        }
        for (final Vuelo vuelo : instanciaProblema.getVuelos()) {
            indiceVuelos.put(vuelo.getIdVuelo(), vuelo);
        }
    }

    public MetricasSolucion evaluar(final CromosomaGenetico cromosoma) {
        if (cromosoma == null) {
            return new MetricasSolucion(0, 0, 0, 0, 0, 0, 0, 0.0);
        }
        int totalMaletas = 0;
        int asignadas = 0;
        int sinRuta = 0;
        int violacionesPlazo = 0;
        int totalSegmentos = 0;
        final Map<String, Integer> cargaPorVuelo = new HashMap<>();

        for (final Map.Entry<String, Integer> entrada : cromosoma.getSeleccionRutaPorMaleta().entrySet()) {
            totalMaletas++;
            final String idMaleta = entrada.getKey();
            final int indiceRuta = entrada.getValue();
            final List<RutaCandidata> rutas = rutasPorMaleta.getOrDefault(idMaleta, List.of());
            if (rutas.isEmpty() || indiceRuta < 0 || indiceRuta >= rutas.size()) {
                sinRuta++;
                continue;
            }
            final RutaCandidata ruta = rutas.get(indiceRuta);
            asignadas++;
            totalSegmentos += ruta.longitud();
            if (!ruta.isCumpleLlegadaADestino()) {
                violacionesPlazo++;
            }
            for (final String idVuelo : ruta.getIdVuelos()) {
                cargaPorVuelo.merge(idVuelo, 1, Integer::sum);
            }
        }

        int vuelosSobrecargados = 0;
        int sumaSobrecarga = 0;
        for (final Map.Entry<String, Integer> entrada : cargaPorVuelo.entrySet()) {
            final Vuelo vuelo = indiceVuelos.get(entrada.getKey());
            if (vuelo == null) {
                continue;
            }
            final int carga = entrada.getValue();
            if (carga > vuelo.getCapacidadMaxima()) {
                vuelosSobrecargados++;
                sumaSobrecarga += carga - vuelo.getCapacidadMaxima();
            }
        }

        final double penalizacion = configuracion.getPesoMaletaNoAsignada() * sinRuta
            + configuracion.getPesoViolacionPlazo() * violacionesPlazo
            + configuracion.getPesoSobrecargaCapacidad() * sumaSobrecarga
            + configuracion.getPesoLongitudRuta() * totalSegmentos;

        return new MetricasSolucion(
            totalMaletas,
            asignadas,
            sinRuta,
            violacionesPlazo,
            vuelosSobrecargados,
            sumaSobrecarga,
            totalSegmentos,
            penalizacion
        );
    }

    public double fitness(final CromosomaGenetico cromosoma) {
        return evaluar(cromosoma).getPenalizacionTotal();
    }

    public Map<String, List<RutaCandidata>> getRutasPorMaleta() {
        return rutasPorMaleta;
    }

    public MaletaProcesada buscarMaleta(final String idMaleta) {
        return indiceMaletas.get(idMaleta);
    }

    public Vuelo buscarVuelo(final String idVuelo) {
        return indiceVuelos.get(idVuelo);
    }
}
