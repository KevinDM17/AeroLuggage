package pe.edu.pucp.aeroluggage.algoritmos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public class InstanciaProblema {
    private String idInstanciaProblema;
    private ArrayList<Maleta> maletas;
    private ArrayList<VueloProgramado> vuelosProgramados;
    private ArrayList<VueloInstancia> vuelosInstancia;
    private ArrayList<Aeropuerto> aeropuertos;

    public InstanciaProblema() {
        this.maletas = new ArrayList<>();
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
        this.idInstanciaProblema = idInstanciaProblema;
        setMaletas(maletas);
        setVuelosProgramados(vuelosProgramados);
        setVuelosInstancia(vuelosInstancia);
        setAeropuertos(aeropuertos);
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
    }

    public ArrayList<VueloProgramado> getVuelosProgramados() {
        return new ArrayList<>(vuelosProgramados);
    }

    public void setVuelosProgramados(final ArrayList<VueloProgramado> vuelosProgramados) {
        this.vuelosProgramados = vuelosProgramados == null ? new ArrayList<>() : new ArrayList<>(vuelosProgramados);
    }

    public ArrayList<VueloInstancia> getVuelosInstancia() {
        return new ArrayList<>(vuelosInstancia);
    }

    public void setVuelosInstancia(final ArrayList<VueloInstancia> vuelosInstancia) {
        this.vuelosInstancia = vuelosInstancia == null ? new ArrayList<>() : new ArrayList<>(vuelosInstancia);
    }

    public ArrayList<VueloProgramado> getVuelos() {
        return getVuelosProgramados();
    }

    public void setVuelos(final ArrayList<VueloProgramado> vuelos) {
        setVuelosProgramados(vuelos);
        setVuelosInstancia(crearInstanciasCompatibles(vuelos));
    }

    public ArrayList<Aeropuerto> getAeropuertos() {
        return new ArrayList<>(aeropuertos);
    }

    public void setAeropuertos(final ArrayList<Aeropuerto> aeropuertos) {
        this.aeropuertos = aeropuertos == null ? new ArrayList<>() : new ArrayList<>(aeropuertos);
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
        if (idVueloProgramado == null || idVueloProgramado.isBlank() || vuelosProgramados == null
                || vuelosProgramados.isEmpty()) {
            return null;
        }
        for (final VueloProgramado vuelo : vuelosProgramados) {
            final boolean coincideVuelo = vuelo != null && idVueloProgramado.equals(vuelo.getIdVueloProgramado());
            if (coincideVuelo) {
                return vuelo;
            }
        }
        return null;
    }

    public VueloInstancia buscarVueloInstancia(final String idVueloInstancia) {
        if (idVueloInstancia == null || idVueloInstancia.isBlank() || vuelosInstancia == null
                || vuelosInstancia.isEmpty()) {
            return null;
        }
        for (final VueloInstancia vuelo : vuelosInstancia) {
            final boolean coincideVuelo = vuelo != null && idVueloInstancia.equals(vuelo.getIdVueloInstancia());
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
                + ", vuelosProgramados=" + vuelosProgramados.size()
                + ", vuelosInstancia=" + vuelosInstancia.size()
                + ", aeropuertos=" + aeropuertos.size()
                + '}';
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
