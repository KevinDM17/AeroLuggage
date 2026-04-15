package pe.edu.pucp.aeroluggage.algorithms.aco;

public class HeuristicaACO {
    private double pesoTiempoEspera;
    private double pesoTiempoVuelo;
    private double pesoCapacidad;
    private double pesoUrgencia;

    public HeuristicaACO() {
        this.pesoTiempoEspera = 1.0D;
        this.pesoTiempoVuelo = 1.0D;
        this.pesoCapacidad = 1.0D;
        this.pesoUrgencia = 1.0D;
    }

    public HeuristicaACO(final double pesoTiempoEspera, final double pesoTiempoVuelo,
        final double pesoCapacidad, final double pesoUrgencia) {
        this.pesoTiempoEspera = pesoTiempoEspera;
        this.pesoTiempoVuelo = pesoTiempoVuelo;
        this.pesoCapacidad = pesoCapacidad;
        this.pesoUrgencia = pesoUrgencia;
    }

    public double calcularValor(final double tiempoEspera, final double tiempoVuelo, final double capacidadRemanente,
        final double urgencia) {
        final double componenteTiempoEspera = tiempoEspera * pesoTiempoEspera;
        final double componenteTiempoVuelo = tiempoVuelo * pesoTiempoVuelo;
        final double componenteCapacidad = capacidadRemanente * pesoCapacidad;
        final double componenteUrgencia = urgencia * pesoUrgencia;
        return 1.0D / (1.0D + componenteTiempoEspera + componenteTiempoVuelo + componenteUrgencia)
            + componenteCapacidad;
    }

    @Override
    public String toString() {
        return "HeuristicaACO{"
            + "pesoTiempoEspera=" + pesoTiempoEspera
            + ", pesoTiempoVuelo=" + pesoTiempoVuelo
            + ", pesoCapacidad=" + pesoCapacidad
            + ", pesoUrgencia=" + pesoUrgencia
            + '}';
    }
}
