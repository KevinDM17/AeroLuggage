package pe.edu.pucp.aeroluggage.algoritmos;

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

public class InstanciaProblema {
    private String idInstanciaProblema;
    private ArrayList<Maleta> maletas;
    private ArrayList<VueloProgramado> vuelos;
    private ArrayList<Aeropuerto> aeropuertos;
    private ArrayList<Pedido> pedidos;
    private ArrayList<VueloInstancia> vueloInstancias;
    private GrafoTiempoExpandido grafo;

    public InstanciaProblema() {
        this.maletas = new ArrayList<>();
        this.vuelos = new ArrayList<>();
        this.aeropuertos = new ArrayList<>();
        this.pedidos = new ArrayList<>();
        this.vueloInstancias = new ArrayList<>();
    }

    public InstanciaProblema(final String idInstanciaProblema, final ArrayList<Maleta> maletas,
                             final ArrayList<VueloProgramado> vuelos, final ArrayList<Aeropuerto> aeropuertos) {
        this();
        this.idInstanciaProblema = idInstanciaProblema;
        this.maletas = maletas != null ? maletas : new ArrayList<>();
        this.vuelos = vuelos != null ? vuelos : new ArrayList<>();
        this.aeropuertos = aeropuertos != null ? aeropuertos : new ArrayList<>();
    }

    public void construirGrafo() {
        this.grafo = GrafoTiempoExpandido.construir(vueloInstancias);
    }

    public Map<String, Aeropuerto> indexarAeropuertosPorIcao() {
        final Map<String, Aeropuerto> indice = new HashMap<>();
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto != null && aeropuerto.getIdAeropuerto() != null) {
                indice.put(aeropuerto.getIdAeropuerto(), aeropuerto);
            }
        }
        return indice;
    }

    public String getIdInstanciaProblema() {
        return idInstanciaProblema;
    }

    public void setIdInstanciaProblema(final String idInstanciaProblema) {
        this.idInstanciaProblema = idInstanciaProblema;
    }

    public ArrayList<Maleta> getMaletas() {
        return maletas;
    }

    public void setMaletas(final ArrayList<Maleta> maletas) {
        this.maletas = maletas != null ? maletas : new ArrayList<>();
    }

    public ArrayList<VueloProgramado> getVuelos() {
        return vuelos;
    }

    public void setVuelos(final ArrayList<VueloProgramado> vuelos) {
        this.vuelos = vuelos != null ? vuelos : new ArrayList<>();
    }

    public ArrayList<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public void setAeropuertos(final ArrayList<Aeropuerto> aeropuertos) {
        this.aeropuertos = aeropuertos != null ? aeropuertos : new ArrayList<>();
    }

    public ArrayList<Pedido> getPedidos() {
        return pedidos;
    }

    public void setPedidos(final ArrayList<Pedido> pedidos) {
        this.pedidos = pedidos != null ? pedidos : new ArrayList<>();
    }

    public ArrayList<VueloInstancia> getVueloInstancias() {
        return vueloInstancias;
    }

    public void setVueloInstancias(final ArrayList<VueloInstancia> vueloInstancias) {
        this.vueloInstancias = vueloInstancias != null ? vueloInstancias : new ArrayList<>();
    }

    public GrafoTiempoExpandido getGrafo() {
        return grafo;
    }

    public void setGrafo(final GrafoTiempoExpandido grafo) {
        this.grafo = grafo;
    }

    public List<Pedido> pedidos() {
        return pedidos;
    }
}
