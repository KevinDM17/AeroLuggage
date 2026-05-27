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

    /* Las listas pesadas (pedidos, maletas, rutas, aeropuertos) crecen sin
     * parar a lo largo de la simulacion. Cada snapshot pesado, al llegar al
     * front, dispara un `JSON.parse` de cientos de KB que bloquea el main
     * thread ~30-50 ms — eso se ve como un hiccup en la animacion. Lo
     * espaciamos a cada 10 ticks (10 s) para que el bloqueo se sienta lo
     * minimo posible. El panel lateral aguanta 10 s de lag sin problema.
     *
     * El frontend tiene guards `Array.isArray(...) ? ... : prev`, asi que un
     * `null` no rompe nada: conserva el ultimo snapshot recibido. */
    private static final int SNAPSHOT_PESADO_CADA_N_TICKS = 10;

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

        // Solo recalcular el snapshot completo cada N ticks. En los ticks
        // ligeros enviamos null y el frontend conserva el snapshot anterior.
        final boolean incluyeSnapshotPesado = tick == 1 || tick % SNAPSHOT_PESADO_CADA_N_TICKS == 0;
        final SimulacionSnapshotService.EntidadesVisibles entidades = incluyeSnapshotPesado
                ? snapshotService.mapearEntidadesVisibles(
                        sesion.getRutas(),
                        sesion.getMaletas(),
                        sesion.getCurrentSimTimeUtc().get())
                : null;

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
                /* Aeropuertos: solo en el snapshot pesado. Sus coordenadas
                 * y capacidades no cambian; solo `maletasActuales` (un
                 * contador). El front conserva el ultimo set recibido. */
                .withAeropuertos(incluyeSnapshotPesado
                        ? snapshotService.mapearAeropuertos(sesion.getAeropuertos())
                        : null)
                .withVuelosInstancia(snapshotService.mapearVuelosInstanciaActivos(
                        sesion.getVuelosInstancia(),
                        sesion.getCurrentSimTimeUtc().get(),
                        sesion.getWindowSizeMinutes()))
                .withPedidos(entidades != null ? entidades.pedidos() : null)
                .withMaletas(entidades != null ? entidades.maletas() : null)
                .withRutas(entidades != null ? entidades.rutas() : null)
                .build();
    }
}
