package pe.edu.pucp.aeroluggage.simulacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.common.DijkstraRuteador;
import pe.edu.pucp.aeroluggage.algoritmos.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.algoritmos.ga.ParametrosGA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public final class Replanificador {

    public static final class ResultadoReplanificacion {
        private final int rutasAfectadas;
        private final int rutasRecuperadas;
        private final int rutasFallidas;
        private final List<String> idsMaletasFallidas;

        public ResultadoReplanificacion(final int rutasAfectadas, final int rutasRecuperadas,
                                        final int rutasFallidas, final List<String> idsMaletasFallidas) {
            this.rutasAfectadas = rutasAfectadas;
            this.rutasRecuperadas = rutasRecuperadas;
            this.rutasFallidas = rutasFallidas;
            this.idsMaletasFallidas = idsMaletasFallidas != null
                    ? Collections.unmodifiableList(idsMaletasFallidas)
                    : Collections.emptyList();
        }

        public int getRutasAfectadas() {
            return rutasAfectadas;
        }

        public int getRutasRecuperadas() {
            return rutasRecuperadas;
        }

        public int getRutasFallidas() {
            return rutasFallidas;
        }

        public List<String> getIdsMaletasFallidas() {
            return idsMaletasFallidas;
        }

        @Override
        public String toString() {
            return "ResultadoReplanificacion{afectadas=" + rutasAfectadas
                    + ", recuperadas=" + rutasRecuperadas
                    + ", fallidas=" + rutasFallidas + '}';
        }
    }

    private Replanificador() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static ResultadoReplanificacion aplicar(final EventoCancelacion evento, final Solucion solucion,
                                                   final InstanciaProblema instancia,
                                                   final ParametrosGA parametros) {
        if (evento == null || solucion == null || instancia == null) {
            return new ResultadoReplanificacion(0, 0, 0, Collections.emptyList());
        }
        final Map<String, VueloInstancia> vuelos = indexarVuelos(instancia);
        final VueloInstancia vueloCancelado = vuelos.get(evento.getIdVueloInstancia());
        if (vueloCancelado != null) {
            vueloCancelado.cancelar();
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            return new ResultadoReplanificacion(0, 0, 0, Collections.emptyList());
        }
        final Map<String, Maleta> maletas = indexarMaletas(instancia);

        int afectadas = 0;
        int recuperadas = 0;
        int fallidas = 0;
        final List<String> idsFallidas = new ArrayList<>();

        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            final int idxCancelado = indiceVuelo(ruta.getSubrutas(), evento.getIdVueloInstancia());
            if (idxCancelado < 0) {
                continue;
            }
            final VueloInstancia cancelado = ruta.getSubrutas().get(idxCancelado);
            if (cancelado != null && cancelado.getFechaSalida() != null
                    && !evento.getMomentoCancelacion().isBefore(cancelado.getFechaSalida())) {
                continue;
            }
            afectadas++;
            final Maleta maleta = maletas.get(ruta.getIdMaleta());
            final boolean exito = replanificarRuta(ruta, idxCancelado, evento, maleta, grafo, parametros);
            if (exito) {
                recuperadas++;
            } else {
                fallidas++;
                idsFallidas.add(ruta.getIdMaleta());
            }
        }

        solucion.calcularMetricas();
        return new ResultadoReplanificacion(afectadas, recuperadas, fallidas, idsFallidas);
    }

    private static boolean replanificarRuta(final Ruta ruta, final int idxCancelado,
                                            final EventoCancelacion evento, final Maleta maleta,
                                            final GrafoTiempoExpandido grafo, final ParametrosGA parametros) {
        if (maleta == null || maleta.getPedido() == null) {
            marcarFallida(ruta);
            return false;
        }
        final Pedido pedido = maleta.getPedido();
        final List<VueloInstancia> subrutas = ruta.getSubrutas();
        final int prefijoMax = prefijoConsolidado(subrutas, evento.getMomentoCancelacion(), idxCancelado);
        final List<VueloInstancia> prefijo = prefijoMax > 0
                ? new ArrayList<>(subrutas.subList(0, prefijoMax))
                : new ArrayList<>();

        final Aeropuerto origenResumen = prefijo.isEmpty()
                ? pedido.getAeropuertoOrigen()
                : prefijo.get(prefijo.size() - 1).getAeropuertoDestino();
        final LocalDateTime tResumen = prefijo.isEmpty()
                ? evento.getMomentoCancelacion().isAfter(pedido.getFechaRegistro())
                        ? evento.getMomentoCancelacion()
                        : pedido.getFechaRegistro()
                : prefijo.get(prefijo.size() - 1).getFechaLlegada();

        final Set<String> bloqueados = new HashSet<>();
        bloqueados.add(evento.getIdVueloInstancia());

        List<VueloInstancia> suffix = DijkstraRuteador.rutear(
                origenResumen, pedido.getAeropuertoDestino(),
                tResumen, pedido.getFechaHoraPlazo(), grafo,
                parametros.getMinutosConexion(), bloqueados);

        if (suffix == null) {
            final LocalDateTime plazoExtendido = pedido.getFechaHoraPlazo() != null
                    ? pedido.getFechaHoraPlazo().plusHours(24)
                    : tResumen.plusHours(48);
            suffix = DijkstraRuteador.rutear(
                    origenResumen, pedido.getAeropuertoDestino(),
                    tResumen, plazoExtendido, grafo,
                    parametros.getMinutosConexion(), bloqueados);
            if (suffix == null) {
                aplicarPrefijoSolo(ruta, prefijo);
                marcarFallida(ruta);
                return false;
            }
            aplicarRuta(ruta, prefijo, suffix, EstadoRuta.REPLANIFICADA);
            return false;
        }
        aplicarRuta(ruta, prefijo, suffix, EstadoRuta.REPLANIFICADA);
        return true;
    }

    private static int prefijoConsolidado(final List<VueloInstancia> subrutas, final LocalDateTime tCancel,
                                          final int idxCancelado) {
        int prefijo = 0;
        for (int i = 0; i < idxCancelado; i++) {
            final VueloInstancia v = subrutas.get(i);
            if (v == null || v.getFechaLlegada() == null || v.getFechaLlegada().isAfter(tCancel)) {
                break;
            }
            prefijo = i + 1;
        }
        return prefijo;
    }

    private static int indiceVuelo(final List<VueloInstancia> subrutas, final String idVuelo) {
        if (idVuelo == null) {
            return -1;
        }
        for (int i = 0; i < subrutas.size(); i++) {
            final VueloInstancia v = subrutas.get(i);
            if (v != null && idVuelo.equals(v.getIdVueloInstancia())) {
                return i;
            }
        }
        return -1;
    }

    private static void aplicarRuta(final Ruta ruta, final List<VueloInstancia> prefijo,
                                    final List<VueloInstancia> suffix, final EstadoRuta estado) {
        final List<VueloInstancia> nuevoCamino = new ArrayList<>(prefijo.size() + suffix.size());
        nuevoCamino.addAll(prefijo);
        nuevoCamino.addAll(suffix);
        ruta.setSubrutas(nuevoCamino);
        ruta.setEstado(nuevoCamino.isEmpty() ? EstadoRuta.FALLIDA : estado);
        ruta.setDuracion(duracionHoras(nuevoCamino));
    }

    private static void aplicarPrefijoSolo(final Ruta ruta, final List<VueloInstancia> prefijo) {
        ruta.setSubrutas(new ArrayList<>(prefijo));
        ruta.setDuracion(duracionHoras(prefijo));
    }

    private static void marcarFallida(final Ruta ruta) {
        ruta.setEstado(EstadoRuta.FALLIDA);
    }

    private static Map<String, Maleta> indexarMaletas(final InstanciaProblema instancia) {
        final Map<String, Maleta> indice = new HashMap<>();
        if (instancia.getMaletas() == null) {
            return indice;
        }
        for (final Maleta m : instancia.getMaletas()) {
            if (m != null && m.getIdMaleta() != null) {
                indice.put(m.getIdMaleta(), m);
            }
        }
        return indice;
    }

    private static Map<String, VueloInstancia> indexarVuelos(final InstanciaProblema instancia) {
        final Map<String, VueloInstancia> indice = new HashMap<>();
        if (instancia.getVueloInstancias() == null) {
            return indice;
        }
        for (final VueloInstancia v : instancia.getVueloInstancias()) {
            if (v != null && v.getIdVueloInstancia() != null) {
                indice.put(v.getIdVueloInstancia(), v);
            }
        }
        return indice;
    }

    private static double duracionHoras(final List<VueloInstancia> camino) {
        if (camino == null || camino.isEmpty()) {
            return 0.0;
        }
        final LocalDateTime inicio = camino.get(0).getFechaSalida();
        final LocalDateTime fin = camino.get(camino.size() - 1).getFechaLlegada();
        if (inicio == null || fin == null) {
            return 0.0;
        }
        return Duration.between(inicio, fin).toMinutes() / 60.0;
    }
}
