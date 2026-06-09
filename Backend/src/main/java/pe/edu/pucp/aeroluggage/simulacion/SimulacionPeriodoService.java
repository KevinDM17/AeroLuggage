package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.config.SimulacionParams;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoVueloDTO;
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
            sesion.podarEventosPasados(cutoff);
        }
        snapshotService.recalcularEstadoSesion(sesion);

        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();

        final SimulacionSesion.TickSnapshot snap = sesion.consolidar(simTimeUtc);

        final SimulacionTickLigeroDTO dto = SimulacionTickLigeroDTO.builder()
                .withType("TICK")
                .withTick(tick)
                .withSimTime(sesion.getCurrentSimTimeUtc().get().format(FORMATO_FECHA_HORA))
                .withVentanaActual(ventana.getWindowId())
                .withStateVersion(sesion.getStateVersion().get())
                .withMaletasEnTransito(snap.enTransito())
                .withMaletasEntregadas(snap.entregadas())
                .withMaletasRetrasadas(0)
                .withMaletasNoAsignadas(snap.sinRuta())
                .withVuelosActivos(snap.vuelosActivos())
                .withCapacidadLibrePct(snap.capacidadLibrePct())
                .withEstadosMaletas(snap.estadosMaletas())
                .withEstadosRutas(snap.estadosRutas())
                .withEstadosVuelos(snap.estadosVuelos())
                .withAeropuertos(snap.aeropuertos())
                .build();

        if (tick == 1 || tick % 10 == 0) {
            int programados = 0, confirmados = 0, enProgreso = 0, finalizados = 0, cancelados = 0;
            for (final EstadoVueloDTO ev : snap.estadosVuelos()) {
                switch (ev.getE()) {
                    case 0 -> programados++;
                    case 1 -> confirmados++;
                    case 2 -> enProgreso++;
                    case 3 -> finalizados++;
                    case 4 -> cancelados++;
                }
            }
            log.info("[TICK] tick={} | simTime={} | vuelos={} [PG={} CF={} EP={} FN={} CN={}] | maletas={} [AL={} TR={} EN={}] | rutas={} | aeropuertos={} | sinRuta={}",
                    tick, simTimeUtc,
                    snap.estadosVuelos().size(), programados, confirmados, enProgreso, finalizados, cancelados,
                    snap.estadosMaletas().size(), snap.almacen(), snap.enTransito(), snap.entregadas(),
                    snap.estadosRutas().size(),
                    snap.aeropuertos().size(),
                    snap.sinRuta());
        }

        return dto;
    }
}
