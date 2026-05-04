package pe.edu.pucp.aeroluggage.algoritmos.common;

import java.util.Properties;

public final class ConfigFitnessExperimental {

    private double w1NoEnrutadas     = 10_000.0;
    private double w2DestinMal       = 10_000.0;
    private double w3OverflowVuelos  =  1_000.0;
    private double w4OverflowAlmacen =  1_000.0;
    private double w5Duracion        =     30.0;
    private double w6Escalas         =     20.0;
    private double w7Espera          =     10.0;

    public ConfigFitnessExperimental() {
    }

    public static ConfigFitnessExperimental desdeProperties(final Properties p) {
        final ConfigFitnessExperimental c = new ConfigFitnessExperimental();
        c.w1NoEnrutadas     = dbl(p, "exp.w1NoEnrutadas",     c.w1NoEnrutadas);
        c.w2DestinMal       = dbl(p, "exp.w2DestinMal",       c.w2DestinMal);
        c.w3OverflowVuelos  = dbl(p, "exp.w3OverflowVuelos",  c.w3OverflowVuelos);
        c.w4OverflowAlmacen = dbl(p, "exp.w4OverflowAlmacen", c.w4OverflowAlmacen);
        c.w5Duracion        = dbl(p, "exp.w5Duracion",        c.w5Duracion);
        c.w6Escalas         = dbl(p, "exp.w6Escalas",         c.w6Escalas);
        c.w7Espera          = dbl(p, "exp.w7Espera",          c.w7Espera);
        return c;
    }

    private static double dbl(final Properties p, final String key, final double def) {
        final String v = p.getProperty(key);
        return v != null ? Double.parseDouble(v.trim()) : def;
    }

    public double getW1NoEnrutadas() {
        return w1NoEnrutadas;
    }

    public double getW2DestinMal() {
        return w2DestinMal;
    }

    public double getW3OverflowVuelos() {
        return w3OverflowVuelos;
    }

    public double getW4OverflowAlmacen() {
        return w4OverflowAlmacen;
    }

    public double getW5Duracion() {
        return w5Duracion;
    }

    public double getW6Escalas() {
        return w6Escalas;
    }

    public double getW7Espera() {
        return w7Espera;
    }
}
