package pe.edu.pucp.aeroluggage.algorithms;

public class MaletaProcesada {
    private final String idMaleta;
    private final String idEnvio;
    private final String idAeropuertoSalida;
    private final String idAeropuertoLlegada;
    private final int plazoMaximoDias;

    public MaletaProcesada(final String idMaleta, final String idEnvio, final String idAeropuertoSalida,
        final String idAeropuertoLlegada, final int plazoMaximoDias) {
        this.idMaleta = idMaleta;
        this.idEnvio = idEnvio;
        this.idAeropuertoSalida = idAeropuertoSalida;
        this.idAeropuertoLlegada = idAeropuertoLlegada;
        this.plazoMaximoDias = plazoMaximoDias;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public String getIdEnvio() {
        return idEnvio;
    }

    public String getIdAeropuertoSalida() {
        return idAeropuertoSalida;
    }

    public String getIdAeropuertoLlegada() {
        return idAeropuertoLlegada;
    }

    public int getPlazoMaximoDias() {
        return plazoMaximoDias;
    }
}
