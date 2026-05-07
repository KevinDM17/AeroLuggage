package pe.edu.pucp.aeroluggage.algoritmo.aco;

import java.util.HashMap;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class FeromonasACO {
    private final Map<String, Map<String, Double>> valores;
    private final ACOConfiguracion configuracion;

    FeromonasACO(final ACOConfiguracion configuracion) {
        valores = new HashMap<>();
        this.configuracion = configuracion;
    }

    boolean isEmpty() {
        return valores.isEmpty();
    }

    double obtener(final Maleta maleta, final VueloInstancia vuelo) {
        final String idMaleta = maleta == null ? null : maleta.getIdMaleta();
        final String idVuelo = vuelo == null ? null : vuelo.getIdVueloInstancia();
        if (idMaleta == null || idVuelo == null) {
            return configuracion.getTau0();
        }
        final Map<String, Double> porMaleta = valores.get(idMaleta);
        if (porMaleta == null) {
            return configuracion.getTau0();
        }
        return porMaleta.getOrDefault(idVuelo, configuracion.getTau0());
    }

    void actualizarLocal(final Maleta maleta, final VueloInstancia vuelo) {
        final String idMaleta = maleta == null ? null : maleta.getIdMaleta();
        final String idVuelo = vuelo == null ? null : vuelo.getIdVueloInstancia();
        if (idMaleta == null || idVuelo == null) {
            return;
        }
        final Map<String, Double> porMaleta = valores.computeIfAbsent(idMaleta, key -> new HashMap<>());
        final double valorActual = porMaleta.getOrDefault(idVuelo, configuracion.getTau0());
        final double nuevoValor = (1D - configuracion.getRho()) * valorActual
                + configuracion.getRho() * configuracion.getTau0();
        porMaleta.put(idVuelo, limitar(nuevoValor));
    }

    void evaporarGlobal() {
        for (final Map<String, Double> porMaleta : valores.values()) {
            for (final Map.Entry<String, Double> entry : porMaleta.entrySet()) {
                final double valorEvaporado = (1D - configuracion.getRho()) * entry.getValue();
                entry.setValue(limitar(valorEvaporado));
            }
        }
    }

    void reforzar(final String idMaleta, final VueloInstancia vuelo, final double delta) {
        if (idMaleta == null || idMaleta.isBlank() || vuelo == null || vuelo.getIdVueloInstancia() == null) {
            return;
        }
        final Map<String, Double> porMaleta = valores.computeIfAbsent(idMaleta, key -> new HashMap<>());
        final String idVuelo = vuelo.getIdVueloInstancia();
        final double valorActual = porMaleta.getOrDefault(idVuelo, configuracion.getTau0());
        porMaleta.put(idVuelo, limitar(valorActual + delta));
    }

    FeromonasACO copiarConAdaptacion(final double factorConservacion) {
        final FeromonasACO copia = new FeromonasACO(configuracion);
        for (final Map.Entry<String, Map<String, Double>> entry : valores.entrySet()) {
            final Map<String, Double> porMaleta = new HashMap<>();
            for (final Map.Entry<String, Double> valorVuelo : entry.getValue().entrySet()) {
                porMaleta.put(valorVuelo.getKey(), limitar(valorVuelo.getValue() * factorConservacion));
            }
            copia.valores.put(entry.getKey(), porMaleta);
        }
        return copia;
    }

    private double limitar(final double valor) {
        final double valorMinimo = Math.max(0D, configuracion.getTauMin());
        final double valorMaximo = Math.max(valorMinimo, configuracion.getTauMax());
        return Math.max(valorMinimo, Math.min(valor, valorMaximo));
    }
}
