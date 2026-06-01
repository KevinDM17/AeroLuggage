package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;

final class ALNSInicializador {
    private ALNSInicializador() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static Solucion construir(final InstanciaProblema instancia, final ParametrosALNS parametros) {
        final Solucion solucion = new Solucion();
        if (instancia == null || instancia.getMaletas().isEmpty()) {
            return solucion;
        }
        final ALNSEstado estado = new ALNSEstado(instancia, solucion);
        final List<Maleta> maletas = new ArrayList<>(estado.getMaletasNoComprometidas());
        maletas.sort(ALNSUtil.comparadorUrgenciaInicial(instancia.getFechaEvaluacion()));

        int secuencia = 1;
        for (final Maleta maleta : maletas) {
            final String idRuta = ALNSUtil.siguienteIdRuta(secuencia++);
            final var ruta = ALNSReparador.encontrarMejorInsercion(estado, maleta, parametros, idRuta);
            if (ruta != null) {
                estado.reemplazarRuta(ruta);
            }
        }
        return estado.getSolucionActual();
    }
}
