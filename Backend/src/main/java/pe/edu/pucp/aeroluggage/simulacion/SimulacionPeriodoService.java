package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.config.SimulacionParams;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoMaletaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoRutaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoVueloDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionTickLigeroDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        final SimulacionVentana ventanaAnterior = sesion.getCurrentWindow().get();
        final SimulacionVentana ventana = sesion.refreshCurrentWindow();
        final boolean ventanaCambio = ventanaAnterior == null
                || !ventanaAnterior.getWindowId().equals(ventana.getWindowId());
        if (ventanaCambio) {
            sesion.podarEventosPasados(simTimeUtc.minusMinutes(
                    sesion.getWindowSpacingMinutes() * simulacionParams.getRetencionVentanas()));
        }
        snapshotService.recalcularEstadoSesion(sesion);

        final SimulacionSesion.TickSnapshot snap = sesion.consolidar(simTimeUtc);

        sesion.podarEntidadesAnteriores(simTimeUtc,
                Duration.ofMinutes(simulacionParams.getRetencionPedidosMinutos()),
                Duration.ofMinutes(simulacionParams.getRetencionVuelosMinutos()));

        final Set<String> idsEntregadas = sesion.consumirIdsEntregadasEnTick();
        final Map<String, String> idsCompletadas = sesion.consumirIdsCompletadasEnTick();

        final List<EstadoMaletaDTO> estadosMaletas = new ArrayList<>(
                snapshotService.mapearEstadosMaletas(sesion.getMaletasCalientes(), simTimeUtc)
        );
        for (final String id : idsEntregadas) {
            estadosMaletas.add(EstadoMaletaDTO.builder()
                    .withId(id)
                    .withE(EstadoMaleta.ENTREGADA.ordinal())
                    .build());
        }

        final List<EstadoRutaDTO> estadosRutas = new ArrayList<>(
                snapshotService.mapearEstadosRutas(sesion.getRutas(), simTimeUtc, sesion.getMaletasPorId())
        );
        for (final Map.Entry<String, String> entry : idsCompletadas.entrySet()) {
            estadosRutas.add(EstadoRutaDTO.builder()
                    .withId(entry.getKey())
                    .withE(EstadoRuta.COMPLETADA.ordinal())
                    .withIdMaleta(entry.getValue())
                    .build());
        }

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
                .withEstadosMaletas(estadosMaletas)
                .withEstadosRutas(estadosRutas)
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
