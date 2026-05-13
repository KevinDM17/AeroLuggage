package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.repositorio.MaletaRepositorio;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.PeriodoTickDTO;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SimulacionPeriodoService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MaletaRepositorio maletaRepositorio;

    public PeriodoTickDTO ejecutarTick(final SimulacionSesion sesion) {
        final int tick = sesion.getTickActual().incrementAndGet();
        sesion.avanzarDia();

        final int enTransito = maletaRepositorio.contarPorEstado(EstadoMaleta.EN_TRANSITO);
        final int entregadas = maletaRepositorio.contarPorEstado(EstadoMaleta.ENTREGADA);
        final int retrasadas = maletaRepositorio.contarPorEstado(EstadoMaleta.REPLANIFICANDO);

        return PeriodoTickDTO.builder()
                .withTickActual(tick)
                .withDiaActual(tick)
                .withFechaSimulada(sesion.getFechaSimulada().get().format(FORMATO_FECHA))
                .withMaletasEnTransito(enTransito)
                .withMaletasEntregadas(entregadas)
                .withMaletasRetrasadas(retrasadas)
                .build();
    }
}
