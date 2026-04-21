package pe.edu.pucp.aeroluggage.algoritmos;

import java.util.ArrayList;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

public class InstanciaProblema {
    private String idInstanciaProblema;
    private ArrayList<Maleta> maletas;
    private ArrayList<VueloProgramado> vuelos;
    private ArrayList<Aeropuerto> aeropuertos;

    public InstanciaProblema() {
        this.maletas = new ArrayList<>();
        this.vuelos = new ArrayList<>();
        this.aeropuertos = new ArrayList<>();
    }

    public InstanciaProblema(final String idInstanciaProblema, final ArrayList<Maleta> maletas,
                           final ArrayList<VueloProgramado> vuelos, final ArrayList<Aeropuerto> aeropuertos) {
        this.idInstanciaProblema = idInstanciaProblema;
        setMaletas(maletas);
        setVuelos(vuelos);
        setAeropuertos(aeropuertos);
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
        if (maletas == null) {
            this.maletas = new ArrayList<>();
            return;
        }
        this.maletas = new ArrayList<>(maletas);
    }

    public ArrayList<VueloProgramado> getVuelos() {
        return vuelos;
    }

    public void setVuelos(final ArrayList<VueloProgramado> vuelos) {
        if (vuelos == null) {
            this.vuelos = new ArrayList<>();
            return;
        }
        this.vuelos = new ArrayList<>(vuelos);
    }

    public ArrayList<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public void setAeropuertos(final ArrayList<Aeropuerto> aeropuertos) {
        if (aeropuertos == null) {
            this.aeropuertos = new ArrayList<>();
            return;
        }
        this.aeropuertos = new ArrayList<>(aeropuertos);
    }

    public Pedido buscarPedido(final String idPedido) {
        if (idPedido == null || idPedido.isBlank() || maletas == null || maletas.isEmpty()) {
            return null;
        }
        for (final Maleta maleta : maletas) {
            if (maleta == null) {
                continue;
            }
            final Pedido pedido = maleta.getPedido();
            final boolean coincidePedido = pedido != null && idPedido.equals(pedido.getIdPedido());
            if (coincidePedido) {
                return pedido;
            }
        }
        return null;
    }

    public VueloProgramado buscarVueloProgramado(final String idVueloProgramado) {
        if (idVueloProgramado == null || idVueloProgramado.isBlank() || vuelos == null || vuelos.isEmpty()) {
            return null;
        }
        for (final VueloProgramado vuelo : vuelos) {
            final boolean coincideVuelo = vuelo != null && idVueloProgramado.equals(vuelo.getIdVueloProgramado());
            if (coincideVuelo) {
                return vuelo;
            }
        }
        return null;
    }

    public Aeropuerto buscarAeropuerto(final String idAeropuerto) {
        if (idAeropuerto == null || idAeropuerto.isBlank() || aeropuertos == null || aeropuertos.isEmpty()) {
            return null;
        }
        for (final Aeropuerto aeropuerto : aeropuertos) {
            final boolean coincideAeropuerto = aeropuerto != null && idAeropuerto.equals(aeropuerto.getIdAeropuerto());
            if (coincideAeropuerto) {
                return aeropuerto;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "InstanciaProblema{"
                + "idInstanciaProblema='" + idInstanciaProblema + '\''
                + ", maletas=" + maletas.size()
                + ", vuelos=" + vuelos.size()
                + ", aeropuertos=" + aeropuertos.size()
                + '}';
    }
}
