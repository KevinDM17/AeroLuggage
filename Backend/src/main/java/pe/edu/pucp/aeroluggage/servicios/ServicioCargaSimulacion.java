package pe.edu.pucp.aeroluggage.servicios;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.cargador.CargadorEnvios;
import pe.edu.pucp.aeroluggage.cargador.CargadorEnvios.LectorLotesEnvios;
import pe.edu.pucp.aeroluggage.cargador.DatosEntrada;
import pe.edu.pucp.aeroluggage.config.SimulacionParams;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
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

    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final VueloProgramadoRepositorio vueloProgramadoRepositorio;
    private final SimulacionParams simulacionParams;

    public ServicioCargaSimulacion(final AeropuertoRepositorio aeropuertoRepositorio,
                                   final VueloProgramadoRepositorio vueloProgramadoRepositorio,
                                   final SimulacionParams simulacionParams) {
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.vueloProgramadoRepositorio = vueloProgramadoRepositorio;
        this.simulacionParams = simulacionParams;
    }

    public void cargarDatosSimulacion(final SimulacionSesion sesion, final Path enviosPath) {
        final List<Aeropuerto> aeropuertos = aeropuertoRepositorio.obtenerTodosConCiudad();
        final Map<String, Aeropuerto> indiceAeropuertos = indexarAeropuertos(aeropuertos);
        final List<VueloProgramado> vuelosProgramados = vueloProgramadoRepositorio.obtenerTodos();

        final LectorLotesEnvios lector = CargadorEnvios.crearLectorLotesEnvios(
                enviosPath, indiceAeropuertos, sesion.getFechaInicioUtc());
        final LocalDateTime finPrimeraVentana = sesion.getFechaInicioUtc()
                .plusMinutes(sesion.getWindowSizeMinutes());
        final DatosEntrada primerLote = lector.siguienteLoteHasta(finPrimeraVentana);

        sesion.setSnapshotData(
                new ArrayList<>(aeropuertos),
                new ArrayList<>(vuelosProgramados),
                new ArrayList<>(),
                primerLote.getPedidos(),
                primerLote.getMaletas(),
                new ArrayList<>());

        sesion.setLectorEnvios(lector);
        sesion.setUltimaCargaPedidos(finPrimeraVentana);
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
