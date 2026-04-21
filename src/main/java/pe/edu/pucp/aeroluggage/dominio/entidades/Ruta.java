package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ruta {
    private static final String ESTADO_PENDIENTE_REPLANIFICACION = "PENDIENTE_REPLANIFICACION";
    private static final String ESTADO_REPLANIFICADA = "REPLANIFICADA";

    private String idRuta;
    private String idMaleta;
    private double plazoMaximoDias;
    private double duracion;
    private List<VueloInstancia> subrutas;
    private String estado;

    public Ruta() {
        this.subrutas = new ArrayList<>();
    }

    public Ruta(final String idRuta, final String idMaleta, final double plazoMaximoDias,
                final double duracion, final List<VueloInstancia> subrutas, final String estado) {
        this.idRuta = idRuta;
        this.idMaleta = idMaleta;
        this.plazoMaximoDias = plazoMaximoDias;
        this.duracion = duracion;
        setSubrutas(subrutas);
        this.estado = estado;
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
        if (subrutas == null) {
            this.subrutas = new ArrayList<>();
            return;
        }
        this.subrutas = new ArrayList<>(subrutas);
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    public double calcularPlazo() {
        if (subrutas == null || subrutas.isEmpty()) {
            duracion = 0;
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
            duracion = 0;
            return duracion;
        }
        duracion = Duration.between(salidaMasTemprana, llegadaMasTardia).toMinutes() / (24D * 60D);
        return duracion;
    }

    public void replanificar() {
        if (subrutas == null || subrutas.isEmpty()) {
            duracion = 0;
            estado = ESTADO_PENDIENTE_REPLANIFICACION;
            return;
        }
        calcularPlazo();
        estado = ESTADO_REPLANIFICADA;
    }
}
