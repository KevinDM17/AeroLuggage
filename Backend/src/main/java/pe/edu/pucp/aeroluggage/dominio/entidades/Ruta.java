package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public class Ruta {
    private static final double PLAZO_MISMO_CONTINENTE_DIAS = 1D;
    private static final double PLAZO_DISTINTO_CONTINENTE_DIAS = 2D;

    private String idRuta;
    private String idMaleta;
    private double plazoMaximoDias;
    private double duracion;
    private List<VueloInstancia> subrutas;
    private EstadoRuta estado;

    public Ruta() {
        this.subrutas = new ArrayList<>();
    }

    public Ruta(final String idRuta, final String idMaleta, final double plazoMaximoDias,
                final double duracion, final List<VueloInstancia> subrutas, final EstadoRuta estado) {
        this.idRuta = idRuta;
        this.idMaleta = idMaleta;
        this.plazoMaximoDias = plazoMaximoDias;
        this.duracion = duracion;
        setSubrutas(subrutas);
        this.estado = estado;
    }

    public Ruta(final String idRuta, final String idMaleta, final double plazoMaximoDias,
                final double duracion, final List<VueloInstancia> subrutas, final String estado) {
        this(idRuta, idMaleta, plazoMaximoDias, duracion, subrutas, convertirEstado(estado));
    }

    public static double calcularPlazo(final Aeropuerto origen, final Aeropuerto destino) {
        if (origen == null || destino == null || origen.getCiudad() == null || destino.getCiudad() == null) {
            return PLAZO_DISTINTO_CONTINENTE_DIAS;
        }
        final Continente continenteOrigen = origen.getCiudad().getContinente();
        final Continente continenteDestino = destino.getCiudad().getContinente();
        return continenteOrigen == continenteDestino ? PLAZO_MISMO_CONTINENTE_DIAS : PLAZO_DISTINTO_CONTINENTE_DIAS;
    }

    public String getIdRuta() {
        return idRuta;
    }

    public void setIdRuta(final String idRuta) {
        this.idRuta = idRuta;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public double getPlazoMaximoDias() {
        return plazoMaximoDias;
    }

    public void setPlazoMaximoDias(final double plazoMaximoDias) {
        this.plazoMaximoDias = plazoMaximoDias;
    }

    public double getDuracion() {
        return duracion;
    }

    public void setDuracion(final double duracion) {
        this.duracion = duracion;
    }

    public List<VueloInstancia> getSubrutas() {
        return subrutas;
    }

    public void setSubrutas(final List<VueloInstancia> subrutas) {
        this.subrutas = subrutas == null ? new ArrayList<>() : new ArrayList<>(subrutas);
    }

    public EstadoRuta getEstado() {
        return estado;
    }

    public void setEstado(final EstadoRuta estado) {
        this.estado = estado;
    }

    public void setEstado(final String estado) {
        this.estado = convertirEstado(estado);
    }

    public double calcularPlazo() {
        if (subrutas == null || subrutas.isEmpty()) {
            duracion = 0D;
            return duracion;
        }

        LocalDateTime salidaMasTemprana = null;
        LocalDateTime llegadaMasTardia = null;
        for (final VueloInstancia subruta : subrutas) {
            if (subruta == null) {
                continue;
            }
            final LocalDateTime fechaSalida = subruta.getFechaSalida();
            final LocalDateTime fechaLlegada = subruta.getFechaLlegada();
            if (fechaSalida != null && (salidaMasTemprana == null || fechaSalida.isBefore(salidaMasTemprana))) {
                salidaMasTemprana = fechaSalida;
            }
            if (fechaLlegada != null && (llegadaMasTardia == null || fechaLlegada.isAfter(llegadaMasTardia))) {
                llegadaMasTardia = fechaLlegada;
            }
        }

        final boolean rangoInvalido = salidaMasTemprana == null
                || llegadaMasTardia == null
                || llegadaMasTardia.isBefore(salidaMasTemprana);
        if (rangoInvalido) {
            duracion = 0D;
            return duracion;
        }
        duracion = Duration.between(salidaMasTemprana, llegadaMasTardia).toMinutes() / (24D * 60D);
        return duracion;
    }

    public void replanificar() {
        if (subrutas == null || subrutas.isEmpty()) {
            duracion = 0D;
            estado = EstadoRuta.FALLIDA;
            return;
        }
        calcularPlazo();
        estado = EstadoRuta.REPLANIFICADA;
    }

    private static EstadoRuta convertirEstado(final String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        final String normalizado = estado.trim();
        if ("NO_FACTIBLE".equals(normalizado) || "PENDIENTE_REPLANIFICACION".equals(normalizado)) {
            return EstadoRuta.FALLIDA;
        }
        try {
            return EstadoRuta.valueOf(normalizado);
        } catch (final IllegalArgumentException exception) {
            return EstadoRuta.PLANIFICADA;
        }
    }
}
