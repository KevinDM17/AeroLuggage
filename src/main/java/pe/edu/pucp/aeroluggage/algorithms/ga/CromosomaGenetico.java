package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.aeroluggage.algorithms.Asignacion;

public final class CromosomaGenetico {
    private final Map<String, Integer> seleccionRutaPorMaleta;
    private double fitness;

    public CromosomaGenetico() {
        this.seleccionRutaPorMaleta = new LinkedHashMap<>();
        this.fitness = Double.MAX_VALUE;
    }

    public CromosomaGenetico(final Map<String, Integer> seleccionRutaPorMaleta) {
        this.seleccionRutaPorMaleta = new LinkedHashMap<>(seleccionRutaPorMaleta);
        this.fitness = Double.MAX_VALUE;
    }

    public CromosomaGenetico(final CromosomaGenetico otro) {
        this.seleccionRutaPorMaleta = new LinkedHashMap<>(otro.seleccionRutaPorMaleta);
        this.fitness = otro.fitness;
    }

    public void asignarRuta(final String idMaleta, final int indiceRuta) {
        seleccionRutaPorMaleta.put(idMaleta, indiceRuta);
    }

    public Integer obtenerIndiceRuta(final String idMaleta) {
        return seleccionRutaPorMaleta.get(idMaleta);
    }

    public Map<String, Integer> getSeleccionRutaPorMaleta() {
        return new LinkedHashMap<>(seleccionRutaPorMaleta);
    }

    public int tamanio() {
        return seleccionRutaPorMaleta.size();
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(final double fitness) {
        this.fitness = fitness;
    }

    public List<Asignacion> convertirEnAsignaciones(final Map<String, List<RutaCandidata>> rutasPorMaleta) {
        final java.util.List<Asignacion> asignaciones = new java.util.ArrayList<>();
        for (final Map.Entry<String, Integer> entrada : seleccionRutaPorMaleta.entrySet()) {
            final String idMaleta = entrada.getKey();
            final int indice = entrada.getValue();
            final List<RutaCandidata> rutas = rutasPorMaleta.getOrDefault(idMaleta, List.of());
            if (rutas.isEmpty() || indice < 0 || indice >= rutas.size()) {
                continue;
            }
            for (final String idVuelo : rutas.get(indice).getIdVuelos()) {
                asignaciones.add(new Asignacion(idMaleta, idVuelo));
            }
        }
        return asignaciones;
    }

    public Map<String, Integer> copiaInterna() {
        return new HashMap<>(seleccionRutaPorMaleta);
    }
}
