package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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

    private Solucion solucionActual;
    private final Map<String, Ruta> rutaPorMaleta;
    private final Map<String, Integer> usoPorVuelo;
    private final Map<String, NavigableMap<LocalDateTime, Integer>> eventosAeropuerto;

    ALNSEstado(final InstanciaProblema instancia, final Solucion solucionInicial) {
        this.instancia = instancia;
        this.maletasPorId = ALNSUtil.indexarMaletas(instancia.getMaletas());
        this.rutasComprometidasPorMaleta = new HashMap<>();
        this.ocupacionBaseAeropuerto = instancia.getOcupacionBaseAeropuerto();
        this.idsMaletasComprometidas = new HashSet<>();
        this.universoMaletas = new ArrayList<>(instancia.getMaletas());
        this.rutaPorMaleta = new HashMap<>();
        this.usoPorVuelo = new HashMap<>();
        this.eventosAeropuerto = ALNSUtil.clonarEventos(instancia.getEventosBaseAeropuerto());

        for (final Ruta ruta : instancia.getRutasComprometidas()) {
            if (ruta == null || ruta.getIdMaleta() == null) {
                continue;
            }
            rutasComprometidasPorMaleta.put(ruta.getIdMaleta(), ruta);
            idsMaletasComprometidas.add(ruta.getIdMaleta());
            registrarUsoRuta(ruta);
            registrarEventosRuta(ruta);
        }

        this.solucionActual = solucionInicial == null ? new Solucion() : solucionInicial.clonarProfundo();
        if (this.solucionActual.getSolucion() == null) {
            this.solucionActual.setSolucion(new ArrayList<>());
        }
        final List<Ruta> rutasFiltradas = new ArrayList<>();
        for (final Ruta ruta : this.solucionActual.getSolucion()) {
            if (ruta == null || ruta.getIdMaleta() == null || idsMaletasComprometidas.contains(ruta.getIdMaleta())) {
                continue;
            }
            if (rutaPorMaleta.putIfAbsent(ruta.getIdMaleta(), ruta) == null) {
                rutasFiltradas.add(ruta);
                registrarUsoRuta(ruta);
                registrarEventosRuta(ruta);
            }
        }
        this.solucionActual.setSolucion(new ArrayList<>(rutasFiltradas));
    }

    ALNSEstado clonar() {
        final ALNSEstado clon = new ALNSEstado(this.instancia, this.solucionActual);
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

    Map<String, Ruta> getRutaPorMaleta() {
        return rutaPorMaleta;
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
        for (final Maleta maleta : getMaletasNoComprometidas()) {
            if (!rutaPorMaleta.containsKey(maleta.getIdMaleta())) {
                faltantes.add(maleta);
            }
        }
        return faltantes;
    }

    Ruta obtenerRuta(final String idMaleta) {
        if (idMaleta == null) {
            return null;
        }
        final Ruta ruta = rutaPorMaleta.get(idMaleta);
        if (ruta != null) {
            return ruta;
        }
        return rutasComprometidasPorMaleta.get(idMaleta);
    }

    void reemplazarRuta(final Ruta nuevaRuta) {
        if (nuevaRuta == null || nuevaRuta.getIdMaleta() == null || esComprometida(nuevaRuta.getIdMaleta())) {
            return;
        }
        final Ruta anterior = rutaPorMaleta.get(nuevaRuta.getIdMaleta());
        if (anterior != null) {
            removerRuta(anterior.getIdMaleta());
        }
        agregarRuta(nuevaRuta);
    }

    void agregarRuta(final Ruta ruta) {
        if (ruta == null || ruta.getIdMaleta() == null || esComprometida(ruta.getIdMaleta())) {
            return;
        }
        rutaPorMaleta.put(ruta.getIdMaleta(), ruta);
        registrarUsoRuta(ruta);
        registrarEventosRuta(ruta);
        reconstruirSolucion();
    }

    Ruta removerRuta(final String idMaleta) {
        if (idMaleta == null || esComprometida(idMaleta)) {
            return null;
        }
        final Ruta eliminada = rutaPorMaleta.remove(idMaleta);
        if (eliminada != null) {
            liberarUsoRuta(eliminada);
            liberarEventosRuta(eliminada);
            reconstruirSolucion();
        }
        return eliminada;
    }

    List<Ruta> rutasNoComprometidas() {
        return new ArrayList<>(rutaPorMaleta.values());
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
        for (final ALNSUtil.IntervaloAeropuerto intervalo
                : ALNSUtil.construirIntervalosRuta(ruta, instancia, maletasPorId)) {
            eventosAeropuerto.computeIfAbsent(intervalo.idAeropuerto(), ignored -> new TreeMap<>())
                    .merge(intervalo.inicio(), 1, Integer::sum);
            eventosAeropuerto.computeIfAbsent(intervalo.idAeropuerto(), ignored -> new TreeMap<>())
                    .merge(intervalo.fin(), -1, Integer::sum);
        }
    }

    void liberarEventosRuta(final Ruta ruta) {
        for (final ALNSUtil.IntervaloAeropuerto intervalo
                : ALNSUtil.construirIntervalosRuta(ruta, instancia, maletasPorId)) {
            ajustarEvento(intervalo.idAeropuerto(), intervalo.inicio(), -1);
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
        final ArrayList<Ruta> rutas = new ArrayList<>(rutaPorMaleta.values());
        rutas.sort((primero, segundo) -> {
            if (primero == null || primero.getIdRuta() == null) {
                return -1;
            }
            if (segundo == null || segundo.getIdRuta() == null) {
                return 1;
            }
            return primero.getIdRuta().compareTo(segundo.getIdRuta());
        });
        solucionActual.setSolucion(rutas);
    }

    int siguienteSecuenciaRuta() {
        return rutaPorMaleta.size() + 1;
    }

    boolean estaVacia() {
        return rutaPorMaleta.isEmpty();
    }

    Ruta crearRutaFallida(final Maleta maleta, final int secuencia) {
        final Ruta ruta = ALNSUtil.crearRuta(ALNSUtil.siguienteIdRuta(secuencia), maleta, List.of(), EstadoRuta.FALLIDA);
        ruta.setDuracion(0.0D);
        return ruta;
    }
}
