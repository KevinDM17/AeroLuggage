package pe.edu.pucp.aeroluggage.algorithms.aco;

public class HeuristicaACO {
    private double pesoTiempoEspera;
    private double pesoTiempoVuelo;
    private double pesoHolguraPlazo;
    private double pesoCapacidadRemanente;

    public HeuristicaACO() {
        this.pesoTiempoEspera = 1.0D;
        this.pesoTiempoVuelo = 1.0D;
        this.pesoHolguraPlazo = 1.0D;
        this.pesoCapacidadRemanente = 1.0D;
    }

    public HeuristicaACO(final double pesoTiempoEspera, final double pesoTiempoVuelo,
        final double pesoHolguraPlazo, final double pesoCapacidadRemanente) {
        this.pesoTiempoEspera = pesoTiempoEspera;
        this.pesoTiempoVuelo = pesoTiempoVuelo;
        this.pesoHolguraPlazo = pesoHolguraPlazo;
        this.pesoCapacidadRemanente = pesoCapacidadRemanente;
    }

    public double getPesoTiempoEspera() {
        return pesoTiempoEspera;
    }

    public void setPesoTiempoEspera(final double pesoTiempoEspera) {
        this.pesoTiempoEspera = pesoTiempoEspera;
    }

    public double getPesoTiempoVuelo() {
        return pesoTiempoVuelo;
    }

    public void setPesoTiempoVuelo(final double pesoTiempoVuelo) {
        this.pesoTiempoVuelo = pesoTiempoVuelo;
    }

    public double getPesoHolguraPlazo() {
        return pesoHolguraPlazo;
    }

    public void setPesoHolguraPlazo(final double pesoHolguraPlazo) {
        this.pesoHolguraPlazo = pesoHolguraPlazo;
    }

    public double getPesoCapacidadRemanente() {
        return pesoCapacidadRemanente;
    }

    public void setPesoCapacidadRemanente(final double pesoCapacidadRemanente) {
        this.pesoCapacidadRemanente = pesoCapacidadRemanente;
    }

    public double calcularValor(final double tiempoEspera, final double tiempoVuelo, final double holguraPlazo,
        final double capacidadRemanente) {
        final double componenteTiempoEspera = tiempoEspera * pesoTiempoEspera;
        final double componenteTiempoVuelo = tiempoVuelo * pesoTiempoVuelo;
        final double componenteHolgura = holguraPlazo * pesoHolguraPlazo;
        final double componenteCapacidad = capacidadRemanente * pesoCapacidadRemanente;
        final double denominador =
            1.0D + componenteTiempoEspera + componenteTiempoVuelo + componenteHolgura + componenteCapacidad;
        return 1.0D / denominador;
    }

    @Override
    public String toString() {
        return "HeuristicaACO{"
            + "pesoTiempoEspera=" + pesoTiempoEspera
            + ", pesoTiempoVuelo=" + pesoTiempoVuelo
            + ", pesoHolguraPlazo=" + pesoHolguraPlazo
            + ", pesoCapacidadRemanente=" + pesoCapacidadRemanente
            + '}';
    }
}
