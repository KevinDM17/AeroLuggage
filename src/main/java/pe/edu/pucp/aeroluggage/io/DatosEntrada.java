package pe.edu.pucp.aeroluggage.io;

import java.util.ArrayList;

import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;

public final class DatosEntrada {
    private final ArrayList<Pedido> pedidos;
    private final ArrayList<Maleta> maletas;

    DatosEntrada(final ArrayList<Pedido> pedidos, final ArrayList<Maleta> maletas) {
        this.pedidos = pedidos == null ? new ArrayList<>() : new ArrayList<>(pedidos);
        this.maletas = maletas == null ? new ArrayList<>() : new ArrayList<>(maletas);
    }

    public ArrayList<Pedido> getPedidos() {
        return new ArrayList<>(pedidos);
    }

    public ArrayList<Maleta> getMaletas() {
        return new ArrayList<>(maletas);
    }
}
