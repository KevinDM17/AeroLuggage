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
            sesion.podarEventosPasados(cutoff);
        }
        snapshotService.recalcularEstadoSesion(sesion);

        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();

        final int enTransito = snapshotService.contarMaletasEnTransito(sesion);
        final int entregadas = snapshotService.contarMaletasEntregadas(sesion);
        final int sinRuta = snapshotService.contarMaletasSinRuta(sesion);
        final int vuelosActivos = snapshotService.contarVuelosActivos(sesion);
        final int capacidadLibrePct = snapshotService.calcularCapacidadLibrePct(sesion);

        final SimulacionTickLigeroDTO dto = SimulacionTickLigeroDTO.builder()
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
                .withEstadosVuelos(snapshotService.mapearEstadosVuelos(sesion.getVuelosInstancia(), simTimeUtc))
                .withAeropuertos(snapshotService.mapearOcupacionAeropuertos(sesion.getAeropuertos()))
                .build();

        if (tick == 1 || tick % 10 == 0) {
            int almacen = 0, transito = 0, entregada = 0;
            for (final pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoMaletaDTO em : dto.getEstadosMaletas()) {
                switch (em.getE()) {
                    case 0 -> almacen++;
                    case 1 -> transito++;
                    case 2 -> entregada++;
                }
            }
            int planificadas = 0, activas = 0, completadas = 0, replanificadas = 0;
            for (final pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoRutaDTO er : dto.getEstadosRutas()) {
                switch (er.getE()) {
                    case 0 -> planificadas++;
                    case 1 -> activas++;
                    case 2 -> completadas++;
                    case 3 -> replanificadas++;
                }
            }
            int programados = 0, confirmados = 0, enProgreso = 0, finalizados = 0, cancelados = 0;
            for (final pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoVueloDTO ev : dto.getEstadosVuelos()) {
                switch (ev.getE()) {
                    case 0 -> programados++;
                    case 1 -> confirmados++;
                    case 2 -> enProgreso++;
                    case 3 -> finalizados++;
                    case 4 -> cancelados++;
                }
            }
            log.info("[TICK] tick={} | simTime={} | vuelos={} [PG={} CF={} EP={} FN={} CN={}] | maletas={} [AL={} TR={} EN={}] | rutas={} [PL={} AC={} CO={} RP={}] | aeropuertos={} | sinRuta={}",
                    tick, simTimeUtc,
                    dto.getEstadosVuelos().size(), programados, confirmados, enProgreso, finalizados, cancelados,
                    dto.getEstadosMaletas().size(), almacen, transito, entregada,
                    dto.getEstadosRutas().size(), planificadas, activas, completadas, replanificadas,
                    dto.getAeropuertos().size(),
                    sinRuta);
        }

        return dto;
    }
}
