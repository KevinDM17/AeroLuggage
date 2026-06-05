package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.config.SimulacionParams;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionTickLigeroDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulacionPeriodoService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SimulacionSnapshotService snapshotService;
    private final SimulacionParams simulacionParams;

    public SimulacionTickLigeroDTO ejecutarTick(final SimulacionSesion sesion) {
        final int tick = sesion.getTickActual().incrementAndGet();
        sesion.updateCurrentSimTimeUtc();
        final SimulacionVentana ventanaAnterior = sesion.getCurrentWindow().get();
        final SimulacionVentana ventana = sesion.refreshCurrentWindow();
        final boolean ventanaCambio = ventanaAnterior == null
                || !ventanaAnterior.getWindowId().equals(ventana.getWindowId());
        if (ventanaCambio) {
            final LocalDateTime cutoff = ventana.getStartUtc()
                    .minusMinutes(sesion.getWindowSpacingMinutes() * simulacionParams.getRetencionVentanas());
            sesion.podarEntidadesAnteriores(cutoff);
        }
        snapshotService.recalcularEstadoSesion(sesion);

        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();

        if (tick == 1 || tick % 50 == 0) {
            log.info("[DIAG-TICK] tick={}, simTime={}, vuelosInstancia={}, maletas={}, rutas={}",
                    tick, simTimeUtc,
                    sesion.getVuelosInstancia().size(),
                    sesion.getMaletas().size(),
                    sesion.getRutas().size());
        }

        final int enTransito = snapshotService.contarMaletasEnTransito(sesion);
        final int entregadas = snapshotService.contarMaletasEntregadas(sesion);
        final int sinRuta = snapshotService.contarMaletasSinRuta(sesion);
        final int vuelosActivos = snapshotService.contarVuelosActivos(sesion);
        final int capacidadLibrePct = snapshotService.calcularCapacidadLibrePct(sesion);

        return SimulacionTickLigeroDTO.builder()
                .withType("TICK")
                .withTick(tick)
                .withSimTime(sesion.getCurrentSimTimeUtc().get().format(FORMATO_FECHA_HORA))
                .withVentanaActual(ventana.getWindowId())
                .withStateVersion(sesion.getStateVersion().get())
                .withMaletasEnTransito(enTransito)
                .withMaletasEntregadas(entregadas)
                .withMaletasRetrasadas(0)
                .withMaletasNoAsignadas(sinRuta)
                .withVuelosActivos(vuelosActivos)
                .withCapacidadLibrePct(capacidadLibrePct)
                .withEstadosMaletas(snapshotService.mapearEstadosMaletas(sesion.getMaletas(), simTimeUtc))
                .withEstadosRutas(snapshotService.mapearEstadosRutas(sesion.getRutas(), simTimeUtc, sesion.getMaletasPorId()))
                .withEstadosVuelos(snapshotService.mapearEstadosVuelos(sesion.getVuelosInstancia()))
                .withAeropuertos(snapshotService.mapearOcupacionAeropuertos(sesion.getAeropuertos()))
                .build();
    }
}
