package pe.edu.pucp.aeroluggage.algoritmo.common;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

public final class GrafoTiempoExpandido {

    private final Map<String, List<VueloInstancia>> vuelosPorOrigen;
    private final int totalVuelos;

    private GrafoTiempoExpandido(final Map<String, List<VueloInstancia>> vuelosPorOrigen, final int totalVuelos) {
        this.vuelosPorOrigen = vuelosPorOrigen;
        this.totalVuelos = totalVuelos;
    }

    public static GrafoTiempoExpandido construir(final List<VueloInstancia> instancias) {
        final Map<String, List<VueloInstancia>> indice = new HashMap<>();
        if (instancias == null || instancias.isEmpty()) {
            return new GrafoTiempoExpandido(indice, 0);
        }
        for (final VueloInstancia vuelo : instancias) {
            if (vuelo == null || vuelo.getAeropuertoOrigen() == null) {
                continue;
            }
            final String icao = vuelo.getAeropuertoOrigen().getIdAeropuerto();
            indice.computeIfAbsent(icao, k -> new ArrayList<>()).add(vuelo);
        }
        final Comparator<VueloInstancia> porSalida = Comparator.comparing(VueloInstancia::getFechaSalida);
        for (final List<VueloInstancia> lista : indice.values()) {
            lista.sort(porSalida);
        }
        return new GrafoTiempoExpandido(indice, instancias.size());
    }

    public List<VueloInstancia> vuelosDesde(final String icao, final LocalDateTime tDesde) {
        final List<VueloInstancia> lista = vuelosPorOrigen.get(icao);
        if (lista == null || lista.isEmpty()) {
            return List.of();
        }
        final int idx = primerIndiceDesde(lista, tDesde);
        if (idx >= lista.size()) {
            return List.of();
        }
        return Collections.unmodifiableList(lista.subList(idx, lista.size()));
    }

    public int totalVuelos() {
        return totalVuelos;
    }

    private static int primerIndiceDesde(final List<VueloInstancia> lista, final LocalDateTime tDesde) {
        int lo = 0;
        int hi = lista.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (lista.get(mid).getFechaSalida().isBefore(tDesde)) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
