package pe.edu.pucp.aeroluggage.algorithms;

public class Asignacion {
    private final String idMaleta;
    private final String idVuelo;

    public Asignacion(final String idMaleta, final String idVuelo) {
        this.idMaleta = idMaleta;
        this.idVuelo = idVuelo;
    }

    public Asignacion(final Asignacion asignacion) {
        if (asignacion == null) {
            this.idMaleta = null;
            this.idVuelo = null;
            return;
        }
        this.idMaleta = asignacion.getIdMaleta();
        this.idVuelo = asignacion.getIdVuelo();
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public String getIdVuelo() {
        return idVuelo;
    }
}
