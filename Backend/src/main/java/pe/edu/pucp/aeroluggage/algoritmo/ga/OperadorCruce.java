package pe.edu.pucp.aeroluggage.algoritmo.ga;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public final class OperadorCruce {

    private OperadorCruce() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static Solucion cruzarSoluciones(final Solucion padre1, final Solucion padre2, final Random random) {
        if (padre1 == null || padre1.getSolucion() == null || padre1.getSolucion().isEmpty()) {
            return padre2 != null ? padre2.clonarProfundo() : new Solucion();
        }
        if (padre2 == null || padre2.getSolucion() == null || padre2.getSolucion().isEmpty()) {
            return padre1.clonarProfundo();
        }

        final Map<String, Ruta> rutasPadre1 = indexar(padre1);
        final Map<String, Ruta> rutasPadre2 = indexar(padre2);

        final List<String> claves = new ArrayList<>(rutasPadre1.keySet());
        claves.sort(String::compareTo);
        if (claves.isEmpty()) {
            return padre2.clonarProfundo();
        }

        final Set<String> subsetDesdePadre1 = seleccionarSubset(claves, random);

        final ArrayList<Ruta> rutasHijo = new ArrayList<>(claves.size());
        int secuencia = 1;
        for (final String idMaleta : claves) {
            final Ruta rutaPadre1 = rutasPadre1.get(idMaleta);
            final Ruta rutaPadre2 = rutasPadre2.get(idMaleta);
            final Ruta fuentePreferida = seleccionarMejorRuta(rutaPadre1, rutaPadre2);
            final Ruta fuenteAlterna = fuentePreferida == rutaPadre1 ? rutaPadre2 : rutaPadre1;
            final Ruta fuente = subsetDesdePadre1.contains(idMaleta)
                    ? (fuentePreferida != null ? fuentePreferida : fuenteAlterna)
                    : (fuenteAlterna != null ? fuenteAlterna : fuentePreferida);
            rutasHijo.add(clonar(fuente, secuencia++));
        }

        for (final Map.Entry<String, Ruta> entry : rutasPadre2.entrySet()) {
            if (!rutasPadre1.containsKey(entry.getKey())) {
                rutasHijo.add(clonar(entry.getValue(), secuencia++));
            }
        }

        final Solucion hijo = new Solucion(rutasHijo);
        hijo.calcularMetricas();
        return hijo;
    }

    private static Set<String> seleccionarSubset(final List<String> claves, final Random random) {
        final Set<String> subset = new HashSet<>();
        final int tam = claves.size();
        if (tam == 0) {
            return subset;
        }
        if (tam == 1) {
            if (random.nextBoolean()) {
                subset.add(claves.get(0));
            }
            return subset;
        }
        final double tasa = 0.3 + random.nextDouble() * 0.4;
        for (final String clave : claves) {
            if (random.nextDouble() < tasa) {
                subset.add(clave);
            }
        }
        if (subset.isEmpty()) {
            subset.add(claves.get(random.nextInt(tam)));
        }
        return subset;
    }

    private static Map<String, Ruta> indexar(final Solucion solucion) {
        final Map<String, Ruta> indice = new HashMap<>();
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta != null && ruta.getIdMaleta() != null) {
                indice.put(ruta.getIdMaleta(), ruta);
            }
        }
        return indice;
    }

    private static Ruta clonar(final Ruta original, final int secuencia) {
        if (original == null) {
            return null;
        }
        final List<VueloInstancia> subrutasOriginal = original.getSubrutas();
        final List<VueloInstancia> copiaSubrutas = subrutasOriginal != null
                ? new ArrayList<>(subrutasOriginal)
                : new ArrayList<>();
        final Ruta copia = new Ruta(
                String.format("R%08d", secuencia),
                original.getIdMaleta(),
                original.getPlazoMaximoDias(),
                original.getDuracion(),
                copiaSubrutas,
                original.getEstado());
        return copia;
    }

    private static Ruta seleccionarMejorRuta(final Ruta rutaA, final Ruta rutaB) {
        if (rutaA == null) {
            return rutaB;
        }
        if (rutaB == null) {
            return rutaA;
        }
        final boolean validaA = esRutaValida(rutaA);
        final boolean validaB = esRutaValida(rutaB);
        if (validaA != validaB) {
            return validaA ? rutaA : rutaB;
        }
        if (rutaA.getDuracion() != rutaB.getDuracion()) {
            return rutaA.getDuracion() <= rutaB.getDuracion() ? rutaA : rutaB;
        }
        return rutaA;
    }

    private static boolean esRutaValida(final Ruta ruta) {
        return ruta.getEstado() != null
                && ruta.getEstado() != EstadoRuta.FALLIDA
                && ruta.getSubrutas() != null
                && !ruta.getSubrutas().isEmpty();
    }
}
