package pe.edu.pucp.aeroluggage.algoritmos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmos.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
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

    private Map<String, Aeropuerto> indiceAeropuertos;
    private Map<String, Maleta> indiceMaletas;
    private Map<String, VueloInstancia> indiceVuelos;

    public InstanciaProblema() {
        this.maletas = new ArrayList<>();
        this.pedidos = new ArrayList<>();
        this.vuelosProgramados = new ArrayList<>();
        this.vuelosInstancia = new ArrayList<>();
        this.aeropuertos = new ArrayList<>();
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
