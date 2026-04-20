package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public class Cromosoma {

    private final Map<String, List<VueloInstancia>> genes;
    private double fitness;
    private boolean fitnessCalculado;

    public Cromosoma() {
        this.genes = new HashMap<>();
    }

    public Cromosoma(final Map<String, List<VueloInstancia>> genes) {
        this.genes = genes != null ? genes : new HashMap<>();
    }

    public List<VueloInstancia> rutaDe(final String idMaleta) {
        final List<VueloInstancia> ruta = genes.get(idMaleta);
        return ruta != null ? ruta : List.of();
    }

    public void asignarRuta(final String idMaleta, final List<VueloInstancia> ruta) {
        if (idMaleta == null) {
            return;
        }
        genes.put(idMaleta, ruta != null ? ruta : List.of());
        fitnessCalculado = false;
    }

    public void eliminarRuta(final String idMaleta) {
        genes.remove(idMaleta);
        fitnessCalculado = false;
    }

    public Set<String> idsMaletas() {
        return Collections.unmodifiableSet(genes.keySet());
    }

    public int tamano() {
        return genes.size();
    }

    public Map<String, List<VueloInstancia>> getGenes() {
        return Collections.unmodifiableMap(genes);
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(final double fitness) {
        this.fitness = fitness;
        this.fitnessCalculado = true;
    }

    public boolean isFitnessCalculado() {
        return fitnessCalculado;
    }

    public void invalidarFitness() {
        this.fitnessCalculado = false;
    }

    public Cromosoma clonarProfundo() {
        final Map<String, List<VueloInstancia>> copia = new HashMap<>(genes.size());
        for (final Map.Entry<String, List<VueloInstancia>> entry : genes.entrySet()) {
            copia.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        final Cromosoma clon = new Cromosoma(copia);
        clon.fitness = this.fitness;
        clon.fitnessCalculado = this.fitnessCalculado;
        return clon;
    }

    public Solucion aSolucion(final List<Maleta> maletas) {
        final ArrayList<Ruta> rutas = new ArrayList<>(maletas != null ? maletas.size() : 0);
        if (maletas == null) {
            return new Solucion(rutas);
        }
        int secuencia = 1;
        for (final Maleta maleta : maletas) {
            if (maleta == null) {
                continue;
            }
            final String idMaleta = maleta.getIdMaleta();
            final List<VueloInstancia> subrutas = new ArrayList<>(rutaDe(idMaleta));
            final Pedido pedido = maleta.getPedido();
            final Ruta ruta = new Ruta();
            ruta.setIdRuta(String.format("R%08d", secuencia++));
            ruta.setIdMaleta(idMaleta);
            ruta.setSubrutas(subrutas);
            ruta.setEstado(subrutas.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
            if (pedido != null) {
                ruta.setPlazoMaximoDias(
                        ruta.calcularPlazo(pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino()));
            }
            ruta.setDuracion(calcularDuracionHoras(subrutas));
            rutas.add(ruta);
        }
        final Solucion solucion = new Solucion(rutas);
        if (fitnessCalculado) {
            solucion.setFitness(fitness);
        }
        solucion.calcularMetricas();
        return solucion;
    }

    private static double calcularDuracionHoras(final List<VueloInstancia> subrutas) {
        if (subrutas == null || subrutas.isEmpty()) {
            return 0.0;
        }
        final LocalDateTime inicio = subrutas.get(0).getFechaSalida();
        final LocalDateTime fin = subrutas.get(subrutas.size() - 1).getFechaLlegada();
        if (inicio == null || fin == null) {
            return 0.0;
        }
        return Duration.between(inicio, fin).toMinutes() / 60.0;
    }
}
