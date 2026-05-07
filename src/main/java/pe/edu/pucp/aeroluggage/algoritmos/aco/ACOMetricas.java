package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ACOMetricas {
    private static final int TOP_AEROPUERTOS = 10;

    private final List<FaseHormiga> fasesHormiga = new ArrayList<>();
    private long construirPlanTemporalCalls;
    private long construirPlanTemporalNanos;
    private long planRespetaCapacidadCalls;
    private long planRespetaCapacidadNanos;
    private long puedeReservarCalls;
    private long puedeReservarNanos;
    private long disponibleEnCalls;
    private long disponibleEnNanos;
    private long reconstruirCapacidadesCalls;
    private long reconstruirCapacidadesNanos;
    private long buscarVueloDirectoCalls;
    private long buscarVueloDirectoNanos;
    private long estadosExpandidos;
    private long vuelosInspeccionados;
    private long rutasRechazadasCapacidadVuelo;
    private long rutasRechazadasAlmacenDestino;
    private long rutasRechazadasAlmacenEscala;
    private final Map<String, Integer> rechazosPorAeropuerto = new HashMap<>();
    private final Map<String, Integer> aceptadasPorAeropuerto = new HashMap<>();

    public void registrarConstruirPlanTemporal(final long nanos, final long estados, final long vuelos) {
        construirPlanTemporalCalls++;
        construirPlanTemporalNanos += nanos;
        estadosExpandidos += estados;
        vuelosInspeccionados += vuelos;
    }

    public void registrarPlanRespetaCapacidad(final long nanos) {
        planRespetaCapacidadCalls++;
        planRespetaCapacidadNanos += nanos;
    }

    public void registrarPuedeReservar(final long nanos) {
        puedeReservarCalls++;
        puedeReservarNanos += nanos;
    }

    public void registrarDisponibleEn(final long nanos) {
        disponibleEnCalls++;
        disponibleEnNanos += nanos;
    }

    public void registrarReconstruirCapacidades(final long nanos) {
        reconstruirCapacidadesCalls++;
        reconstruirCapacidadesNanos += nanos;
    }

    public void registrarBuscarVueloDirecto(final long nanos) {
        buscarVueloDirectoCalls++;
        buscarVueloDirectoNanos += nanos;
    }

    public void registrarRechazoCapacidadVuelo() {
        rutasRechazadasCapacidadVuelo++;
    }

    public void registrarRechazoAlmacenDestino(final String idAeropuerto) {
        rutasRechazadasAlmacenDestino++;
        registrarConteo(rechazosPorAeropuerto, idAeropuerto);
    }

    public void registrarRechazoAlmacenEscala(final String idAeropuerto) {
        rutasRechazadasAlmacenEscala++;
        registrarConteo(rechazosPorAeropuerto, idAeropuerto);
    }

    public void registrarRutaAceptadaAeropuerto(final String idAeropuerto) {
        registrarConteo(aceptadasPorAeropuerto, idAeropuerto);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                List.copyOf(fasesHormiga),
                construirPlanTemporalCalls,
                construirPlanTemporalNanos,
                planRespetaCapacidadCalls,
                planRespetaCapacidadNanos,
                puedeReservarCalls,
                puedeReservarNanos,
                disponibleEnCalls,
                disponibleEnNanos,
                reconstruirCapacidadesCalls,
                reconstruirCapacidadesNanos,
                buscarVueloDirectoCalls,
                buscarVueloDirectoNanos,
                estadosExpandidos,
                vuelosInspeccionados,
                rutasRechazadasCapacidadVuelo,
                rutasRechazadasAlmacenDestino,
                rutasRechazadasAlmacenEscala,
                topEntries(rechazosPorAeropuerto),
                topEntries(aceptadasPorAeropuerto)
        );
    }

    public void registrarFaseHormiga(final String fase,
                                     final int iteracion,
                                     final int hormiga,
                                     final long nanos,
                                     final Snapshot antes,
                                     final Snapshot despues) {
        if (fase == null || antes == null || despues == null) {
            return;
        }
        fasesHormiga.add(new FaseHormiga(
                fase,
                iteracion,
                hormiga,
                nanos,
                Math.max(0L, despues.construirPlanTemporalCalls() - antes.construirPlanTemporalCalls()),
                Math.max(0L, despues.construirPlanTemporalNanos() - antes.construirPlanTemporalNanos()),
                Math.max(0L, despues.planRespetaCapacidadCalls() - antes.planRespetaCapacidadCalls()),
                Math.max(0L, despues.planRespetaCapacidadNanos() - antes.planRespetaCapacidadNanos()),
                Math.max(0L, despues.puedeReservarCalls() - antes.puedeReservarCalls()),
                Math.max(0L, despues.puedeReservarNanos() - antes.puedeReservarNanos()),
                Math.max(0L, despues.disponibleEnCalls() - antes.disponibleEnCalls()),
                Math.max(0L, despues.disponibleEnNanos() - antes.disponibleEnNanos()),
                Math.max(0L, despues.reconstruirCapacidadesCalls() - antes.reconstruirCapacidadesCalls()),
                Math.max(0L, despues.reconstruirCapacidadesNanos() - antes.reconstruirCapacidadesNanos()),
                Math.max(0L, despues.buscarVueloDirectoCalls() - antes.buscarVueloDirectoCalls()),
                Math.max(0L, despues.buscarVueloDirectoNanos() - antes.buscarVueloDirectoNanos()),
                Math.max(0L, despues.estadosExpandidos() - antes.estadosExpandidos()),
                Math.max(0L, despues.vuelosInspeccionados() - antes.vuelosInspeccionados()),
                Math.max(0L, despues.rutasRechazadasCapacidadVuelo() - antes.rutasRechazadasCapacidadVuelo()),
                Math.max(0L, despues.rutasRechazadasAlmacenDestino() - antes.rutasRechazadasAlmacenDestino()),
                Math.max(0L, despues.rutasRechazadasAlmacenEscala() - antes.rutasRechazadasAlmacenEscala())
        ));
    }

    private void registrarConteo(final Map<String, Integer> mapa, final String idAeropuerto) {
        if (idAeropuerto == null || idAeropuerto.isBlank()) {
            return;
        }
        mapa.merge(idAeropuerto, 1, Integer::sum);
    }

    private List<Map.Entry<String, Integer>> topEntries(final Map<String, Integer> mapa) {
        final List<Map.Entry<String, Integer>> entradas = new ArrayList<>(mapa.entrySet());
        entradas.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey));
        if (entradas.size() <= TOP_AEROPUERTOS) {
            return List.copyOf(entradas);
        }
        return List.copyOf(entradas.subList(0, TOP_AEROPUERTOS));
    }

    public record Snapshot(List<FaseHormiga> fasesHormiga,
                           long construirPlanTemporalCalls,
                           long construirPlanTemporalNanos,
                           long planRespetaCapacidadCalls,
                           long planRespetaCapacidadNanos,
                           long puedeReservarCalls,
                           long puedeReservarNanos,
                           long disponibleEnCalls,
                           long disponibleEnNanos,
                           long reconstruirCapacidadesCalls,
                           long reconstruirCapacidadesNanos,
                           long buscarVueloDirectoCalls,
                           long buscarVueloDirectoNanos,
                           long estadosExpandidos,
                           long vuelosInspeccionados,
                           long rutasRechazadasCapacidadVuelo,
                           long rutasRechazadasAlmacenDestino,
                           long rutasRechazadasAlmacenEscala,
                           List<Map.Entry<String, Integer>> topRechazosAeropuerto,
                           List<Map.Entry<String, Integer>> topAceptadasAeropuerto) {
    }

    public record FaseHormiga(String fase,
                              int iteracion,
                              int hormiga,
                              long nanos,
                              long construirPlanTemporalCalls,
                              long construirPlanTemporalNanos,
                              long planRespetaCapacidadCalls,
                              long planRespetaCapacidadNanos,
                              long puedeReservarCalls,
                              long puedeReservarNanos,
                              long disponibleEnCalls,
                              long disponibleEnNanos,
                              long reconstruirCapacidadesCalls,
                              long reconstruirCapacidadesNanos,
                              long buscarVueloDirectoCalls,
                              long buscarVueloDirectoNanos,
                              long estadosExpandidos,
                              long vuelosInspeccionados,
                              long rutasRechazadasCapacidadVuelo,
                              long rutasRechazadasAlmacenDestino,
                              long rutasRechazadasAlmacenEscala) {
    }
}
