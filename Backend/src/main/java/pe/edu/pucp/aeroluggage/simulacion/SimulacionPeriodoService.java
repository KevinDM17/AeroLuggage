package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.PeriodoTickDTO;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SimulacionPeriodoService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SimulacionSnapshotService snapshotService;

    public PeriodoTickDTO ejecutarTick(final SimulacionSesion sesion) {
        final int tick = sesion.getTickActual().incrementAndGet();
        sesion.updateCurrentSimTimeUtc();
        final SimulacionVentana ventana = sesion.refreshCurrentWindow();
        snapshotService.recalcularEstadoSesion(sesion);

        final int enTransito = snapshotService.contarMaletasEnTransito(sesion);
        final int entregadas = snapshotService.contarMaletasEntregadas(sesion);
        final int sinRuta = snapshotService.contarMaletasSinRuta(sesion);
        final int vuelosActivos = snapshotService.contarVuelosActivos(sesion);
        final int capacidadLibrePct = snapshotService.calcularCapacidadLibrePct(sesion);
        final long minutosDesdeInicio = Duration.between(sesion.getFechaInicioUtc(), sesion.getCurrentSimTimeUtc().get()).toMinutes();
        final int diaActual = Math.max(1, (int) (minutosDesdeInicio / (24L * 60L)) + 1);
        final SimulacionSnapshotService.EntidadesVisibles entidades =
                snapshotService.mapearEntidadesVisibles(sesion.getRutas(), sesion.getMaletas(), sesion.getCurrentSimTimeUtc().get());

        return PeriodoTickDTO.builder()
                .withTickActual(tick)
                .withDiaActual(Math.min(sesion.getTotalDias(), diaActual))
                .withFechaSimulada(sesion.getCurrentSimTimeUtc().get().toLocalDate().format(FORMATO_FECHA))
                .withCurrentSimTimeUtc(sesion.getCurrentSimTimeUtc().get().format(FORMATO_FECHA_HORA))
                .withCurrentWindowId(ventana.getWindowId())
                .withCurrentWindowStartUtc(ventana.getStartUtc().format(FORMATO_FECHA_HORA))
                .withCurrentWindowEndUtc(ventana.getEndUtc().format(FORMATO_FECHA_HORA))
                .withCurrentWindowStatus(ventana.getStatus())
                .withStateVersion(sesion.getStateVersion().get())
                .withMaletasEnTransito(enTransito)
                .withMaletasEntregadas(entregadas)
                .withMaletasRetrasadas(0)
                .withMaletasNoAsignadas(sinRuta)
                .withVuelosActivos(vuelosActivos)
                .withCapacidadLibrePct(capacidadLibrePct)
                .withAeropuertos(snapshotService.mapearAeropuertos(sesion.getAeropuertos()))
                .withVuelosInstancia(snapshotService.mapearVuelosInstanciaActivos(
                        sesion.getVuelosInstancia(),
                        sesion.getCurrentSimTimeUtc().get(),
                        sesion.getWindowSizeMinutes()))
                .withPedidos(entidades.pedidos())
                .withMaletas(entidades.maletas())
                .withRutas(entidades.rutas())
                .build();
    }
}
