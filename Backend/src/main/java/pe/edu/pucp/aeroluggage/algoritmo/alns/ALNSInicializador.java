package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;

final class ALNSInicializador {
    private ALNSInicializador() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static ResultadoInicial construir(final InstanciaProblema instancia, final ParametrosALNS parametros) {
        final Solucion solucion = new Solucion();
        if (instancia == null || instancia.getMaletas().isEmpty()) {
            return new ResultadoInicial(solucion, Map.of());
        }
        final ALNSEstado estado = new ALNSEstado(instancia, solucion);
        final List<Maleta> maletas = new ArrayList<>(estado.getMaletasNoComprometidas());
        maletas.sort(ALNSUtil.comparadorUrgenciaInicial(instancia.getFechaEvaluacion()));

        for (final Maleta maleta : maletas) {
            var ruta = ALNSReparador.encontrarInsercionValida(estado, maleta, parametros);
            if (ruta == null) {
                ruta = ALNSReparador.encontrarInsercionForzada(estado, maleta, parametros);
            }
            if (ruta != null) {
                estado.reemplazarRuta(ruta);
            }
        }

        final Map<String, String> razonesFallo = estado.getRazonesFallo();

        return new ResultadoInicial(estado.getSolucionActual(), razonesFallo);
    }

    record ResultadoInicial(Solucion solucion, Map<String, String> razonesFallo) {
    }
}
