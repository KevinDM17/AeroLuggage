package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Contenido de un almacen (aeropuerto): los envios (pedidos) y productos
 * (maletas) que estan en el, separados en "destino final" (su destino es este
 * almacen) y "en transito" (esperan aqui el siguiente vuelo). Da soporte a los
 * requisitos de "acceso a envios/productos desde la lista de almacenes".
 *
 * Las listas de maletas vienen acotadas; los totales reflejan la cuenta real.
 */
@Getter
@Builder(setterPrefix = "with")
public class AlmacenContenidoResponse {

    private String idAeropuerto;

    private int totalMaletasDestinoFinal;
    private int totalMaletasEnTransito;

    private List<MaletaSimulacionResponse> maletasDestinoFinal;
    private List<MaletaSimulacionResponse> maletasEnTransito;

    private List<PedidoSimulacionResponse> pedidosDestinoFinal;
    private List<PedidoSimulacionResponse> pedidosEnTransito;

    // Planificado (segun el plan de rutas): movimientos futuros en el almacen.
    private int totalMaletasEntran;
    private int totalMaletasSalen;

    private List<PedidoSimulacionResponse> pedidosEntran;
    private List<PedidoSimulacionResponse> pedidosSalen;

    private List<MovimientoPlanificadoResponse> maletasEntran;
    private List<MovimientoPlanificadoResponse> maletasSalen;
}
