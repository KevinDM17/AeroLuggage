package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

final class ALNSEstado {
    private final InstanciaProblema instancia;
    private final Map<String, Maleta> maletasPorId;
    private final Map<String, Ruta> rutasComprometidasPorMaleta;
    private final Map<String, Integer> ocupacionBaseAeropuerto;
    private final Set<String> idsMaletasComprometidas;
    private final List<Maleta> universoMaletas;
    private final Map<String, Integer> indicePorMaleta;

    private Solucion solucionActual;
    private final Map<String, Integer> usoPorVuelo;
    private final Map<String, NavigableMap<LocalDateTime, Integer>> eventosAeropuerto;
    private final Map<String, String> razonesFallo = new HashMap<>();

    ALNSEstado(final InstanciaProblema instancia, final Solucion solucionInicial) {
        this.instancia = instancia;
        this.maletasPorId = ALNSUtil.indexarMaletas(instancia.getMaletas());
        this.rutasComprometidasPorMaleta = new HashMap<>();
        this.ocupacionBaseAeropuerto = instancia.getOcupacionBaseAeropuerto();
        this.idsMaletasComprometidas = new HashSet<>();
        this.universoMaletas = new ArrayList<>(instancia.getMaletas());
        this.indicePorMaleta = new HashMap<>();
        for (int i = 0; i < universoMaletas.size(); i++) {
            final Maleta m = universoMaletas.get(i);
            if (m != null && m.getIdMaleta() != null) {
                indicePorMaleta.put(m.getIdMaleta(), i);
            }
        }
        this.usoPorVuelo = new HashMap<>();
        this.eventosAeropuerto = ALNSUtil.clonarEventos(instancia.getEventosBaseAeropuerto());

        for (final Ruta ruta : instancia.getRutasComprometidas()) {
            if (ruta == null || ruta.getIdMaleta() == null) {
                continue;
            }
            rutasComprometidasPorMaleta.put(ruta.getIdMaleta(), ruta);
            idsMaletasComprometidas.add(ruta.getIdMaleta());
            if (instancia.getEventosBaseAeropuerto().isEmpty()) {
                registrarEventosRuta(ruta);
            }
        }

        this.solucionActual = solucionInicial == null ? new Solucion() : solucionInicial.clonarProfundo();
        final List<Ruta> sol = this.solucionActual.getSolucion();
        if (sol == null || sol.size() != universoMaletas.size()) {
            final ArrayList<Ruta> fijo = new ArrayList<>(universoMaletas.size());
            for (int i = 0; i < universoMaletas.size(); i++) {
                fijo.add(null);
            }
            if (sol != null) {
                for (final Ruta r : sol) {
                    if (r != null && r.getIdMaleta() != null && !idsMaletasComprometidas.contains(r.getIdMaleta())) {
                        final Integer idx = indicePorMaleta.get(r.getIdMaleta());
                        if (idx != null) {
                            fijo.set(idx, r);
                            registrarUsoRuta(r);
                            registrarEventosRuta(r);
                        }
                    }
                }
            }
            this.solucionActual.setSolucion(fijo);
        } else {
            for (final Ruta r : sol) {
                if (r != null && !idsMaletasComprometidas.contains(r.getIdMaleta())) {
                    registrarUsoRuta(r);
                    registrarEventosRuta(r);
                }
            }
        }
    }

    ALNSEstado clonar() {
        final ALNSEstado clon = new ALNSEstado(this.instancia, this.solucionActual);
        clon.razonesFallo.putAll(this.razonesFallo);
        return clon;
    }

    InstanciaProblema getInstancia() {
        return instancia;
    }

    Solucion getSolucionActual() {
        return solucionActual;
    }

    void setSolucionActual(final Solucion solucionActual) {
        this.solucionActual = solucionActual;
    }

    Map<String, Integer> getUsoPorVuelo() {
        return usoPorVuelo;
    }

    Map<String, NavigableMap<LocalDateTime, Integer>> getEventosAeropuerto() {
        return eventosAeropuerto;
    }

    Map<String, Integer> getOcupacionBaseAeropuerto() {
        return ocupacionBaseAeropuerto;
    }

    Map<String, Maleta> getMaletasPorId() {
        return maletasPorId;
    }

    List<Maleta> getUniversoMaletas() {
        return universoMaletas;
    }

    boolean esComprometida(final String idMaleta) {
        return idsMaletasComprometidas.contains(idMaleta);
    }

    List<Maleta> getMaletasNoComprometidas() {
        final List<Maleta> resultado = new ArrayList<>();
        for (final Maleta maleta : universoMaletas) {
            if (maleta != null && maleta.getIdMaleta() != null && !idsMaletasComprometidas.contains(maleta.getIdMaleta())) {
                resultado.add(maleta);
            }
        }
        return resultado;
    }

    List<Maleta> getMaletasSinRuta() {
        final List<Maleta> faltantes = new ArrayList<>();
        final List<Ruta> solucion = solucionActual.getSolucion();
        for (final Maleta maleta : getMaletasNoComprometidas()) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            final Integer idx = indicePorMaleta.get(maleta.getIdMaleta());
            if (idx == null || solucion == null || idx >= solucion.size() || solucion.get(idx) == null) {
                faltantes.add(maleta);
            }
        }
        return faltantes;
    }

    Ruta obtenerRuta(final String idMaleta) {
        if (idMaleta == null) {
            return null;
        }
        final Integer idx = indicePorMaleta.get(idMaleta);
        if (idx != null) {
            final List<Ruta> solucion = solucionActual.getSolucion();
            if (solucion != null && idx < solucion.size() && solucion.get(idx) != null) {
                return solucion.get(idx);
            }
        }
        return rutasComprometidasPorMaleta.get(idMaleta);
    }

    void reemplazarRuta(final Ruta nuevaRuta) {
        if (nuevaRuta == null || nuevaRuta.getIdMaleta() == null || esComprometida(nuevaRuta.getIdMaleta())) {
            return;
        }
        final Ruta anterior = obtenerRuta(nuevaRuta.getIdMaleta());
        if (anterior != null && !esComprometida(anterior.getIdMaleta())) {
            liberarUsoRuta(anterior);
            liberarEventosRuta(anterior);
        }
        agregarRuta(nuevaRuta);
    }

    void agregarRuta(final Ruta ruta) {
        if (ruta == null || ruta.getIdMaleta() == null || esComprometida(ruta.getIdMaleta())) {
            return;
        }
        final Integer idx = indicePorMaleta.get(ruta.getIdMaleta());
        if (idx == null) {
            return;
        }
        final List<Ruta> solucion = solucionActual.getSolucion();
        if (solucion != null && idx < solucion.size()) {
            solucion.set(idx, ruta);
        }
        registrarUsoRuta(ruta);
        registrarEventosRuta(ruta);
    }

    Ruta removerRuta(final String idMaleta) {
        if (idMaleta == null || esComprometida(idMaleta)) {
            return null;
        }
        final Integer idx = indicePorMaleta.get(idMaleta);
        if (idx == null) {
            return null;
        }
        final List<Ruta> solucion = solucionActual.getSolucion();
        if (solucion == null || idx >= solucion.size()) {
            return null;
        }
        final Ruta eliminada = solucion.set(idx, null);
        if (eliminada != null) {
            liberarUsoRuta(eliminada);
            liberarEventosRuta(eliminada);
        }
        return eliminada;
    }

    List<Ruta> rutasNoComprometidas() {
        final List<Ruta> resultado = new ArrayList<>();
        final List<Ruta> solucion = solucionActual.getSolucion();
        if (solucion == null) {
            return resultado;
        }
        for (final Ruta r : solucion) {
            if (r != null && r.getIdMaleta() != null && !idsMaletasComprometidas.contains(r.getIdMaleta())) {
                resultado.add(r);
            }
        }
        return resultado;
    }

    void registrarUsoRuta(final Ruta ruta) {
        if (ruta == null || ruta.getSubrutas() == null) {
            return;
        }
        for (final VueloInstancia vuelo : ruta.getSubrutas()) {
            if (vuelo != null && vuelo.getIdVueloInstancia() != null) {
                usoPorVuelo.merge(vuelo.getIdVueloInstancia(), 1, Integer::sum);
            }
        }
    }

    void liberarUsoRuta(final Ruta ruta) {
        if (ruta == null || ruta.getSubrutas() == null) {
            return;
        }
        for (final VueloInstancia vuelo : ruta.getSubrutas()) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            final int nuevo = usoPorVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0) - 1;
            if (nuevo <= 0) {
                usoPorVuelo.remove(vuelo.getIdVueloInstancia());
            } else {
                usoPorVuelo.put(vuelo.getIdVueloInstancia(), nuevo);
            }
        }
    }

    void registrarEventosRuta(final Ruta ruta) {
        final LocalDateTime fechaEval = instancia.getFechaEvaluacion();
        for (final ALNSUtil.IntervaloAeropuerto intervalo
                : ALNSUtil.construirIntervalosRuta(ruta, instancia, maletasPorId)) {
            if (intervalo.inicio().isAfter(fechaEval)) {
                eventosAeropuerto.computeIfAbsent(intervalo.idAeropuerto(), ignored -> new TreeMap<>())
                        .merge(intervalo.inicio(), 1, Integer::sum);
            }
            eventosAeropuerto.computeIfAbsent(intervalo.idAeropuerto(), ignored -> new TreeMap<>())
                    .merge(intervalo.fin(), -1, Integer::sum);
        }
    }

    void liberarEventosRuta(final Ruta ruta) {
        final LocalDateTime fechaEval = instancia.getFechaEvaluacion();
        for (final ALNSUtil.IntervaloAeropuerto intervalo
                : ALNSUtil.construirIntervalosRuta(ruta, instancia, maletasPorId)) {
            if (intervalo.inicio().isAfter(fechaEval)) {
                ajustarEvento(intervalo.idAeropuerto(), intervalo.inicio(), -1);
            }
            ajustarEvento(intervalo.idAeropuerto(), intervalo.fin(), 1);
        }
    }

    private void ajustarEvento(final String idAeropuerto, final LocalDateTime momento, final int delta) {
        if (idAeropuerto == null || momento == null) {
            return;
        }
        final NavigableMap<LocalDateTime, Integer> eventos = eventosAeropuerto.get(idAeropuerto);
        if (eventos == null) {
            return;
        }
        final int nuevo = eventos.getOrDefault(momento, 0) + delta;
        if (nuevo == 0) {
            eventos.remove(momento);
        } else {
            eventos.put(momento, nuevo);
        }
        if (eventos.isEmpty()) {
            eventosAeropuerto.remove(idAeropuerto);
        }
    }

    void reconstruirSolucion() {
        // La soluci\u00f3n ya tiene tama\u00f1o fijo; solo reordenar no aplica.
        // Los mapas derivados (usoPorVuelo, eventosAeropuerto) ya se actualizan
        // en agregarRuta/removerRuta.
    }

    int siguienteSecuenciaRuta() {
        final List<Ruta> solucion = solucionActual.getSolucion();
        if (solucion == null) {
            return 1;
        }
        return (int) solucion.stream().filter(Objects::nonNull).count() + 1;
    }

    boolean estaVacia() {
        final List<Ruta> solucion = solucionActual.getSolucion();
        if (solucion == null) {
            return true;
        }
        return solucion.stream().noneMatch(Objects::nonNull);
    }

    void registrarFalloMaleta(final String idMaleta, final String razon) {
        if (idMaleta != null && razon != null) {
            razonesFallo.put(idMaleta, razon);
        }
    }

    Map<String, String> getRazonesFallo() {
        return razonesFallo;
    }

    void limpiarRazonFallo(final String idMaleta) {
        if (idMaleta != null) {
            razonesFallo.remove(idMaleta);
        }
    }
}
