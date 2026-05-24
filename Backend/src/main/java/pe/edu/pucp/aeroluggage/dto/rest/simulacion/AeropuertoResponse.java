package pe.edu.pucp.aeroluggage.dto.rest.simulacion;

import lombok.Builder;
import lombok.Getter;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;

@Getter
@Builder(setterPrefix = "with")
public class AeropuertoResponse {

    private String idAeropuerto;
    private Ciudad ciudad;
    private int capacidadAlmacen;
    private int maletasActuales;
    private float latitud;
    private float longitud;
    private int husoGMT;
}
