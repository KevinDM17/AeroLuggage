package pe.edu.pucp.aeroluggage.algoritmo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import pe.edu.pucp.aeroluggage.algoritmo.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public class InstanciaProblema {
    private String idInstanciaProblema;
    private ArrayList<Maleta> maletas;
    private ArrayList<Pedido> pedidos;
    private ArrayList<VueloProgramado> vuelosProgramados;
    private ArrayList<VueloInstancia> vuelosInstancia;
    private ArrayList<Aeropuerto> aeropuertos;
    private GrafoTiempoExpandido grafo;
    private long minutosConexion = 10L;
    private long tiempoRecojo = 10L;
    private LocalDateTime fechaEvaluacion;
    private ArrayList<Ruta> rutasComprometidas;
    private Map<String, Integer> ocupacionBaseAeropuerto;
    private Map<String, NavigableMap<LocalDateTime, Integer>> eventosBaseAeropuerto;

    private Map<String, Aeropuerto> indiceAeropuertos;
    private Map<String, Maleta> indiceMaletas;
    private Map<String, VueloInstancia> indiceVuelos;

    public InstanciaProblema() {
        this.maletas = new ArrayList<>();
        this.pedidos = new ArrayList<>();
        this.vuelosProgramados = new ArrayList<>();
        this.vuelosInstancia = new ArrayList<>();
        this.aeropuertos = new ArrayList<>();
        this.rutasComprometidas = new ArrayList<>();
        this.ocupacionBaseAeropuerto = new HashMap<>();
        this.eventosBaseAeropuerto = new HashMap<>();
    }

    public InstanciaProblema(final String idInstanciaProblema, final ArrayList<Maleta> maletas,
                             final ArrayList<VueloProgramado> vuelosProgramados,
                             final ArrayList<Aeropuerto> aeropuertos) {
        this(
                idInstanciaProblema,
                maletas,
                vuelosProgramados,
                crearInstanciasCompatibles(vuelosProgramados),
                aeropuertos
        );
    }

    public InstanciaProblema(final String idInstanciaProblema, final ArrayList<Maleta> maletas,
                             final ArrayList<VueloProgramado> vuelosProgramados,
                             final ArrayList<VueloInstancia> vuelosInstancia,
                             final ArrayList<Aeropuerto> aeropuertos) {
        this();
        this.idInstanciaProblema = idInstanciaProblema;
        setMaletas(maletas);
        setVuelosProgramados(vuelosProgramados);
        setVuelosInstancia(vuelosInstancia);
        setAeropuertos(aeropuertos);
    }

    public void construirGrafo() {
        this.grafo = GrafoTiempoExpandido.construir(vuelosInstancia);
    }

    public Map<String, VueloInstancia> getVueloIndex() {
        return getVuelosPorId();
    }

    public Map<String, Aeropuerto> indexarAeropuertosPorIcao() {
        if (indiceAeropuertos == null) {
            final Map<String, Aeropuerto> indice = new HashMap<>();
            for (final Aeropuerto aeropuerto : aeropuertos) {
                if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                    continue;
                }
                indice.put(aeropuerto.getIdAeropuerto(), aeropuerto);
            }
            indiceAeropuertos = indice;
        }
        return indiceAeropuertos;
    }

    public Map<String, Maleta> getMaletasPorId() {
        if (indiceMaletas == null) {
            final Map<String, Maleta> indice = new HashMap<>();
            for (final Maleta maleta : maletas) {
                if (maleta != null && maleta.getIdMaleta() != null) {
                    indice.put(maleta.getIdMaleta(), maleta);
                }
            }
            indiceMaletas = indice;
        }
        return indiceMaletas;
    }

    public Map<String, VueloInstancia> getVuelosPorId() {
        if (indiceVuelos == null) {
            final Map<String, VueloInstancia> indice = new HashMap<>();
            for (final VueloInstancia vuelo : vuelosInstancia) {
                if (vuelo != null && vuelo.getIdVueloInstancia() != null) {
                    indice.put(vuelo.getIdVueloInstancia(), vuelo);
                }
            }
            indiceVuelos = indice;
        }
        return indiceVuelos;
    }

    public String getIdInstanciaProblema() {
        return idInstanciaProblema;
    }

    public void setIdInstanciaProblema(final String idInstanciaProblema) {
        this.idInstanciaProblema = idInstanciaProblema;
    }

    public ArrayList<Maleta> getMaletas() {
        return new ArrayList<>(maletas);
    }

    public void setMaletas(final ArrayList<Maleta> maletas) {
        this.maletas = maletas == null ? new ArrayList<>() : new ArrayList<>(maletas);
        this.indiceMaletas = null;
        reconstruirPedidosDesdeMaletas();
    }

    public ArrayList<Pedido> getPedidos() {
        return new ArrayList<>(pedidos);
    }

    public void setPedidos(final ArrayList<Pedido> pedidos) {
        this.pedidos = pedidos == null ? new ArrayList<>() : new ArrayList<>(pedidos);
    }

    public List<Pedido> pedidos() {
        return getPedidos();
    }

    public ArrayList<VueloProgramado> getVuelosProgramados() {
        return new ArrayList<>(vuelosProgramados);
    }

    public void setVuelosProgramados(final ArrayList<VueloProgramado> vuelosProgramados) {
        this.vuelosProgramados = vuelosProgramados == null ? new ArrayList<>() : new ArrayList<>(vuelosProgramados);
    }

    public ArrayList<VueloProgramado> getVuelos() {
        return getVuelosProgramados();
    }

    public void setVuelos(final ArrayList<VueloProgramado> vuelos) {
        setVuelosProgramados(vuelos);
        setVuelosInstancia(crearInstanciasCompatibles(vuelos));
    }

    public ArrayList<VueloInstancia> getVuelosInstancia() {
        return new ArrayList<>(vuelosInstancia);
    }

    public void setVuelosInstancia(final ArrayList<VueloInstancia> vuelosInstancia) {
        this.vuelosInstancia = vuelosInstancia == null ? new ArrayList<>() : new ArrayList<>(vuelosInstancia);
        this.indiceVuelos = null;
    }

    public ArrayList<VueloInstancia> getVueloInstancias() {
        return getVuelosInstancia();
    }

    public void setVueloInstancias(final ArrayList<VueloInstancia> vueloInstancias) {
        setVuelosInstancia(vueloInstancias);
    }

    public ArrayList<Aeropuerto> getAeropuertos() {
        return new ArrayList<>(aeropuertos);
    }

    public void setAeropuertos(final ArrayList<Aeropuerto> aeropuertos) {
        this.aeropuertos = aeropuertos == null ? new ArrayList<>() : new ArrayList<>(aeropuertos);
        this.indiceAeropuertos = null;
    }

    public GrafoTiempoExpandido getGrafo() {
        return grafo;
    }

    public void setGrafo(final GrafoTiempoExpandido grafo) {
        this.grafo = grafo;
    }

    public long getMinutosConexion() {
        return minutosConexion;
    }

    public void setMinutosConexion(final long minutosConexion) {
        this.minutosConexion = minutosConexion;
    }

    public long getTiempoRecojo() {
        return tiempoRecojo;
    }

    public void setTiempoRecojo(final long tiempoRecojo) {
        this.tiempoRecojo = tiempoRecojo;
    }

    public LocalDateTime getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public void setFechaEvaluacion(final LocalDateTime fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    public ArrayList<Ruta> getRutasComprometidas() {
        return new ArrayList<>(rutasComprometidas);
    }

    public void setRutasComprometidas(final List<Ruta> rutasComprometidas) {
        this.rutasComprometidas = rutasComprometidas == null
                ? new ArrayList<>()
                : new ArrayList<>(rutasComprometidas);
    }

    public Map<String, Integer> getOcupacionBaseAeropuerto() {
        return new HashMap<>(ocupacionBaseAeropuerto);
    }

    public void setOcupacionBaseAeropuerto(final Map<String, Integer> ocupacionBaseAeropuerto) {
        this.ocupacionBaseAeropuerto = ocupacionBaseAeropuerto == null
                ? new HashMap<>()
                : new HashMap<>(ocupacionBaseAeropuerto);
    }

    public Map<String, NavigableMap<LocalDateTime, Integer>> getEventosBaseAeropuerto() {
        final Map<String, NavigableMap<LocalDateTime, Integer>> copia = new HashMap<>();
        for (final Map.Entry<String, NavigableMap<LocalDateTime, Integer>> entry : eventosBaseAeropuerto.entrySet()) {
            copia.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        }
        return copia;
    }

    public void setEventosBaseAeropuerto(final Map<String, NavigableMap<LocalDateTime, Integer>> eventosBaseAeropuerto) {
        final Map<String, NavigableMap<LocalDateTime, Integer>> copia = new HashMap<>();
        if (eventosBaseAeropuerto != null) {
            for (final Map.Entry<String, NavigableMap<LocalDateTime, Integer>> entry : eventosBaseAeropuerto.entrySet()) {
                copia.put(entry.getKey(), entry.getValue() == null ? new TreeMap<>() : new TreeMap<>(entry.getValue()));
            }
        }
        this.eventosBaseAeropuerto = copia;
    }

    public Pedido buscarPedido(final String idPedido) {
        if (idPedido == null || idPedido.isBlank()) {
            return null;
        }
        for (final Pedido pedido : pedidos) {
            if (pedido != null && idPedido.equals(pedido.getIdPedido())) {
                return pedido;
            }
        }
        return null;
    }

    public VueloProgramado buscarVueloProgramado(final String idVueloProgramado) {
        if (idVueloProgramado == null || idVueloProgramado.isBlank()) {
            return null;
        }
        for (final VueloProgramado vuelo : vuelosProgramados) {
            if (vuelo != null && idVueloProgramado.equals(vuelo.getIdVueloProgramado())) {
                return vuelo;
            }
        }
        return null;
    }

    public VueloInstancia buscarVueloInstancia(final String idVueloInstancia) {
        if (idVueloInstancia == null || idVueloInstancia.isBlank()) {
            return null;
        }
        for (final VueloInstancia vuelo : vuelosInstancia) {
            if (vuelo != null && idVueloInstancia.equals(vuelo.getIdVueloInstancia())) {
                return vuelo;
            }
        }
        return null;
    }

    public Aeropuerto buscarAeropuerto(final String idAeropuerto) {
        if (idAeropuerto == null || idAeropuerto.isBlank()) {
            return null;
        }
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto != null && idAeropuerto.equals(aeropuerto.getIdAeropuerto())) {
                return aeropuerto;
            }
        }
        return null;
    }

    public InstanciaProblema deepCopy() {
        final Map<String, Aeropuerto> aeroIdx = new HashMap<>();
        final ArrayList<Aeropuerto> aeroCopias = new ArrayList<>(this.aeropuertos.size());
        for (final Aeropuerto a : this.aeropuertos) {
            if (a == null) {
                continue;
            }
            final Aeropuerto copia = new Aeropuerto(
                    a.getIdAeropuerto(),
                    a.getCiudad(),
                    a.getCapacidadAlmacen(),
                    a.getMaletasActuales(),
                    a.getLongitud(),
                    a.getLatitud(),
                    a.getHusoGMT()
            );
            aeroIdx.put(copia.getIdAeropuerto(), copia);
            aeroCopias.add(copia);
        }

        final Map<String, VueloInstancia> viIdx = new HashMap<>();
        final ArrayList<VueloInstancia> viCopias = new ArrayList<>(this.vuelosInstancia.size());
        for (final VueloInstancia v : this.vuelosInstancia) {
            if (v == null) {
                continue;
            }
            final VueloInstancia copia = new VueloInstancia(v, aeroIdx);
            viIdx.put(copia.getIdVueloInstancia(), copia);
            viCopias.add(copia);
        }

        final ArrayList<Maleta> maletasCopias = new ArrayList<>(this.maletas.size());
        for (final Maleta m : this.maletas) {
            if (m == null) {
                continue;
            }
            maletasCopias.add(new Maleta(m, aeroIdx));
        }

        final ArrayList<Ruta> rutasCopias = new ArrayList<>(this.rutasComprometidas.size());
        for (final Ruta r : this.rutasComprometidas) {
            if (r == null) {
                continue;
            }
            final List<VueloInstancia> subrutasResueltas = r.getSubrutaIds().stream()
                    .map(viIdx::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            final Ruta copiaRuta = new Ruta(
                    r.getIdRuta(), r.getIdMaleta(),
                    r.getPlazoMaximoDias(), r.getDuracion(),
                    subrutasResueltas, r.getEstado());
            rutasCopias.add(copiaRuta);
        }

        final InstanciaProblema copia = new InstanciaProblema();
        copia.idInstanciaProblema = this.idInstanciaProblema;
        copia.aeropuertos = aeroCopias;
        copia.vuelosProgramados = new ArrayList<>(this.vuelosProgramados);
        copia.vuelosInstancia = viCopias;
        copia.setMaletas(maletasCopias);
        copia.rutasComprometidas = rutasCopias;
        copia.ocupacionBaseAeropuerto = new HashMap<>(this.ocupacionBaseAeropuerto);

        final Map<String, NavigableMap<LocalDateTime, Integer>> eventosCopia = new HashMap<>();
        if (this.eventosBaseAeropuerto != null) {
            for (final Map.Entry<String, NavigableMap<LocalDateTime, Integer>> entry : this.eventosBaseAeropuerto.entrySet()) {
                eventosCopia.put(entry.getKey(), entry.getValue() == null ? new TreeMap<>() : new TreeMap<>(entry.getValue()));
            }
        }
        copia.eventosBaseAeropuerto = eventosCopia;

        copia.fechaEvaluacion = this.fechaEvaluacion;
        copia.minutosConexion = this.minutosConexion;
        copia.tiempoRecojo = this.tiempoRecojo;
        copia.indiceAeropuertos = null;
        copia.indiceMaletas = null;
        copia.indiceVuelos = null;
        return copia;
    }

    @Override
    public String toString() {
        return "InstanciaProblema{"
                + "idInstanciaProblema='" + idInstanciaProblema + '\''
                + ", pedidos=" + pedidos.size()
                + ", maletas=" + maletas.size()
                + ", vuelosProgramados=" + vuelosProgramados.size()
                + ", vuelosInstancia=" + vuelosInstancia.size()
                + ", aeropuertos=" + aeropuertos.size()
                + '}';
    }

    private void reconstruirPedidosDesdeMaletas() {
        final Map<String, Pedido> pedidosPorId = new HashMap<>();
        for (final Maleta maleta : this.maletas) {
            if (maleta == null || maleta.getPedido() == null || maleta.getPedido().getIdPedido() == null) {
                continue;
            }
            pedidosPorId.putIfAbsent(maleta.getPedido().getIdPedido(), maleta.getPedido());
        }
        this.pedidos = new ArrayList<>(pedidosPorId.values());
    }

    private static ArrayList<VueloInstancia> crearInstanciasCompatibles(
            final ArrayList<VueloProgramado> vuelosProgramados
    ) {
        final ArrayList<VueloInstancia> instancias = new ArrayList<>();
        if (vuelosProgramados == null) {
            return instancias;
        }
        final LocalDate fechaBase = LocalDate.of(2026, 4, 20);
        for (final VueloProgramado vueloProgramado : vuelosProgramados) {
            if (vueloProgramado == null || vueloProgramado.getHoraSalida() == null
                    || vueloProgramado.getHoraLlegada() == null) {
                continue;
            }
            final LocalDateTime salida = LocalDateTime.of(fechaBase, vueloProgramado.getHoraSalida());
            LocalDateTime llegada = LocalDateTime.of(fechaBase, vueloProgramado.getHoraLlegada());
            if (!llegada.isAfter(salida)) {
                llegada = llegada.plusDays(1);
            }
            instancias.add(new VueloInstancia(
                    vueloProgramado.getIdVueloProgramado(),
                    vueloProgramado,
                    fechaBase,
                    salida,
                    llegada,
                    vueloProgramado.getCapacidadBase(),
                    vueloProgramado.getCapacidadBase(),
                    EstadoVuelo.PROGRAMADO
            ));
        }
        return instancias;
    }
}
