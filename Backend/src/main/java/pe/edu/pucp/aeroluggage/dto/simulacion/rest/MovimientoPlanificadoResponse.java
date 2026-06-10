package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Builder;
import lombok.Getter;

/**
 * Un movimiento planificado de un producto (maleta) en un almacen: la maleta
 * (y su envio) que, segun el plan de rutas, va a entrar (aterrizar) o salir
 * (despegar) del almacen, con el vuelo y la hora prevista.
 */
@Getter
@Builder(setterPrefix = "with")
public class MovimientoPlanificadoResponse {

    private String idMaleta;
    private String idPedido;
    private String vuelo;
    private String hora;   // fecha-hora planificada (ISO local date-time)
}
