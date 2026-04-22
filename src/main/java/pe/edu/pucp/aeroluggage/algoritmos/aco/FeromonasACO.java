package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.util.HashMap;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

final class FeromonasACO {
    private static final String SEPARADOR_CLAVE = "::";

    private final Map<String, Double> valores;
    private final ACOConfiguracion configuracion;

    FeromonasACO(final ACOConfiguracion configuracion) {
        valores = new HashMap<>();
        this.configuracion = configuracion;
    }

    boolean isEmpty() {
        return valores.isEmpty();
    }

    double obtener(final Maleta maleta, final VueloProgramado vuelo) {
        final String clave = construirClave(maleta, vuelo);
        if (clave == null) {
            return configuracion.getTau0();
        }
        return valores.getOrDefault(clave, configuracion.getTau0());
    }

    void actualizarLocal(final Maleta maleta, final VueloProgramado vuelo) {
        final String clave = construirClave(maleta, vuelo);
        if (clave == null) {
            return;
        }
        final double valorActual = valores.getOrDefault(clave, configuracion.getTau0());
        final double nuevoValor = (1D - configuracion.getRho()) * valorActual
                + configuracion.getRho() * configuracion.getTau0();
        valores.put(clave, limitar(nuevoValor));
    }

    void evaporarGlobal() {
        for (final Map.Entry<String, Double> entry : valores.entrySet()) {
            final double valorEvaporado = (1D - configuracion.getRho()) * entry.getValue();
            entry.setValue(limitar(valorEvaporado));
        }
    }

    void reforzar(final String idMaleta, final VueloInstancia vuelo, final double delta) {
        if (idMaleta == null || idMaleta.isBlank() || vuelo == null || vuelo.getIdVueloInstancia() == null) {
            return;
        }
        final String clave = construirClave(idMaleta, vuelo.getIdVueloInstancia());
        final double valorActual = valores.getOrDefault(clave, configuracion.getTau0());
        valores.put(clave, limitar(valorActual + delta));
    }

    FeromonasACO copiarConAdaptacion(final double factorConservacion) {
        final FeromonasACO copia = new FeromonasACO(configuracion);
        for (final Map.Entry<String, Double> entry : valores.entrySet()) {
            copia.valores.put(entry.getKey(), limitar(entry.getValue() * factorConservacion));
        }
        return copia;
    }

    private String construirClave(final Maleta maleta, final VueloProgramado vuelo) {
        if (maleta == null || maleta.getIdMaleta() == null || vuelo == null || vuelo.getIdVueloProgramado() == null) {
            return null;
        }
        return construirClave(maleta.getIdMaleta(), vuelo.getIdVueloProgramado());
    }

    private String construirClave(final String idMaleta, final String idVuelo) {
        return idMaleta + SEPARADOR_CLAVE + idVuelo;
    }

    private double limitar(final double valor) {
        final double valorMinimo = Math.max(0D, configuracion.getTauMin());
        final double valorMaximo = Math.max(valorMinimo, configuracion.getTauMax());
        return Math.max(valorMinimo, Math.min(valor, valorMaximo));
    }
}
