package pe.edu.pucp.aeroluggage.simulacion.DataTransferObject;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(setterPrefix = "with")
public class OperacionEventoDTO {

    private String tipoEvento;
    private String entidadAfectada;
    private String idEntidad;
    private LocalDateTime timestamp;
    private Object payload;
}
