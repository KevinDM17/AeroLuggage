package pe.edu.pucp.aeroluggage.servicios;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.cargador.CargadorEnvios;
import pe.edu.pucp.aeroluggage.cargador.DatosEntrada;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.VueloProgramadoRepositorio;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesion;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ServicioCargaSimulacion {

    private static final long MAX_DIAS_VUELOS_INSTANCIAS = 30L;

    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final VueloProgramadoRepositorio vueloProgramadoRepositorio;

    public ServicioCargaSimulacion(final AeropuertoRepositorio aeropuertoRepositorio,
                                   final VueloProgramadoRepositorio vueloProgramadoRepositorio) {
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.vueloProgramadoRepositorio = vueloProgramadoRepositorio;
    }

    public void cargarDatosSimulacion(final SimulacionSesion sesion, final Path enviosPath) {
        final List<Aeropuerto> aeropuertos = aeropuertoRepositorio.obtenerTodosConCiudad();
        final Map<String, Aeropuerto> indiceAeropuertos = indexarAeropuertos(aeropuertos);

        final List<VueloProgramado> vuelosProgramados = vueloProgramadoRepositorio.obtenerTodos();

        final LocalDate fechaInicio = sesion.getFechaInicio();
        final LocalDate fechaFin = fechaInicio.plusDays(Math.max(0L, sesion.getTotalDias() - 1L));
        final long diasVuelos = Math.min(sesion.getTotalDias() + 2L, MAX_DIAS_VUELOS_INSTANCIAS);
        final LocalDate fechaFinVuelos = fechaInicio.plusDays(diasVuelos);

        final List<VueloInstancia> vuelosInstancia = generarVuelosInstancia(
                vuelosProgramados, fechaInicio, fechaFinVuelos);

        log.info("[AeroLuggage/Simulacion] - SNAPSHOT: sessionId={}, totalDias={}, diasVuelos={}, vuelosInstancia={}",
                sesion.getSessionId(), sesion.getTotalDias(), diasVuelos, vuelosInstancia.size());

        final DatosEntrada datosEntrada = CargadorEnvios.cargarEnviosEnRango(
                enviosPath, indiceAeropuertos, fechaInicio, fechaFin);

        final ArrayList<Pedido> pedidos = new ArrayList<>(datosEntrada.getPedidos());
        pedidos.sort(Comparator.comparing(Pedido::getFechaRegistro).thenComparing(Pedido::getIdPedido));
        final ArrayList<Maleta> maletas = new ArrayList<>(datosEntrada.getMaletas());
        maletas.sort(Comparator.comparing(Maleta::getFechaRegistro).thenComparing(Maleta::getIdMaleta));

        sesion.setSnapshotData(aeropuertos, vuelosProgramados, vuelosInstancia, pedidos, maletas, new ArrayList<>());
    }

    private static List<VueloInstancia> generarVuelosInstancia(final List<VueloProgramado> programados,
                                                               final LocalDate fechaInicio,
                                                               final LocalDate fechaFin) {
        if (programados == null || programados.isEmpty() || fechaFin.isBefore(fechaInicio)) {
            return List.of();
        }
        final List<VueloInstancia> instancias = new ArrayList<>();
        int secuencia = 1;
        LocalDate dia = fechaInicio;
        while (!dia.isAfter(fechaFin)) {
            for (final VueloProgramado programado : programados) {
                final LocalDateTime salidaLocal = LocalDateTime.of(dia, programado.getHoraSalida());
                LocalDateTime llegadaLocal = LocalDateTime.of(dia, programado.getHoraLlegada());
                if (!llegadaLocal.isAfter(salidaLocal)) {
                    llegadaLocal = llegadaLocal.plusDays(1);
                }
                final LocalDateTime salidaUtc = salidaLocal.minusHours(
                        programado.getAeropuertoOrigen().getHusoGMT());
                final LocalDateTime llegadaUtc = llegadaLocal.minusHours(
                        programado.getAeropuertoDestino().getHusoGMT());

                final String id = String.format("VI%08d", secuencia++);
                instancias.add(new VueloInstancia(
                        id,
                        programado.getCodigo(),
                        salidaUtc,
                        llegadaUtc,
                        programado.getCapacidadMaxima(),
                        programado.getCapacidadMaxima(),
                        programado.getAeropuertoOrigen(),
                        programado.getAeropuertoDestino(),
                        EstadoVuelo.PROGRAMADO
                ));
            }
            dia = dia.plusDays(1);
        }
        return instancias;
    }

    private static Map<String, Aeropuerto> indexarAeropuertos(final List<Aeropuerto> aeropuertos) {
        final Map<String, Aeropuerto> indice = new HashMap<>();
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                continue;
            }
            indice.put(aeropuerto.getIdAeropuerto(), aeropuerto);
        }
        return indice;
    }
}
