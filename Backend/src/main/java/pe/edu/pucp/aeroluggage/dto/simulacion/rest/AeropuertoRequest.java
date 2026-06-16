package pe.edu.pucp.aeroluggage.dto.simulacion.rest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AeropuertoRequest {

    private String idAeropuerto;
    private String nombreCiudad;
    private String continente;
    private int capacidadAlmacen;
    private int maletasActuales;
    private float latitud;
    private float longitud;
    private int husoGMT;
}
