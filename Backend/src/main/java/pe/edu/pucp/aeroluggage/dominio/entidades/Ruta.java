package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public class Ruta {
    private String idRuta;
    private String idMaleta;
    private double plazoMaximoDias;
    private double duracion;
    private List<String> subrutaIds;
    private EstadoRuta estado;
    private LocalDateTime fechaEntrega;
    private List<VueloInstancia> subrutasCache;

    public Ruta() {
        this.subrutaIds = new ArrayList<>();
        this.subrutasCache = new ArrayList<>();
    }

    public Ruta(final String idRuta, final String idMaleta, final double plazoMaximoDias,
                final double duracion, final List<VueloInstancia> subrutas, final EstadoRuta estado) {
        this(idRuta, idMaleta, plazoMaximoDias, duracion, subrutas, estado, null);
    }

    public Ruta(final String idRuta, final String idMaleta, final double plazoMaximoDias,
                final double duracion, final List<VueloInstancia> subrutas, final EstadoRuta estado,
                final LocalDateTime fechaEntrega) {
        this.idRuta = idRuta;
        this.idMaleta = idMaleta;
        this.plazoMaximoDias = plazoMaximoDias;
        this.duracion = duracion;
        setSubrutas(subrutas);
        this.estado = estado;
        this.fechaEntrega = fechaEntrega;
    }

    public Ruta(final Ruta other) {
        this.idRuta = other.idRuta;
        this.idMaleta = other.idMaleta;
        this.plazoMaximoDias = other.plazoMaximoDias;
        this.duracion = other.duracion;
        this.subrutaIds = other.subrutaIds == null ? null : new ArrayList<>(other.subrutaIds);
        this.estado = other.estado;
        this.fechaEntrega = other.fechaEntrega;
        this.subrutasCache = null;
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

    public LocalDateTime getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(final LocalDateTime fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public List<VueloInstancia> getSubrutas() {
        return subrutasCache == null ? List.of() : subrutasCache;
    }

    public void setSubrutas(final List<VueloInstancia> subrutas) {
        if (subrutas == null) {
            this.subrutaIds = new ArrayList<>();
            this.subrutasCache = new ArrayList<>();
            return;
        }
        final ArrayList<String> ids = new ArrayList<>(subrutas.size());
        for (final VueloInstancia v : subrutas) {
            ids.add(v == null ? null : v.getIdVueloInstancia());
        }
        this.subrutaIds = ids;
        this.subrutasCache = new ArrayList<>(subrutas);
    }

    public List<String> getSubrutaIds() {
        return subrutaIds == null ? List.of() : new ArrayList<>(subrutaIds);
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
        final List<VueloInstancia> subrutas = getSubrutas();
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
        final List<VueloInstancia> subrutas = getSubrutas();
        if (subrutas == null || subrutas.isEmpty()) {
            duracion = 0D;
            estado = EstadoRuta.REPLANIFICADA;
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
            return EstadoRuta.REPLANIFICADA;
        }
        try {
            return EstadoRuta.valueOf(normalizado);
        } catch (final IllegalArgumentException exception) {
            return EstadoRuta.PLANIFICADA;
        }
    }
}
