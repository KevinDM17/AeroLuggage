package pe.edu.pucp.aeroluggage.servicios.query;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import pe.edu.pucp.aeroluggage.cargador.GeneradorVueloInstancias;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.SimulacionInicioResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.VueloInstanciaResponse;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.VueloProgramadoRepositorio;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.SimulacionEstadoDTO;

@Service
public class SimulacionInicioQueryService {

    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final VueloProgramadoRepositorio vueloProgramadoRepositorio;

    public SimulacionInicioQueryService(final AeropuertoRepositorio aeropuertoRepositorio,
                                        final VueloProgramadoRepositorio vueloProgramadoRepositorio) {
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.vueloProgramadoRepositorio = vueloProgramadoRepositorio;
    }

    public SimulacionInicioResponse construirRespuestaInicio(final SimulacionEstadoDTO estado,
                                                             final LocalDate fechaInicio,
                                                             final int totalDias) {
        final LocalDate fechaFin = fechaInicio.plusDays(Math.max(0, totalDias - 1L));
        final List<AeropuertoResponse> aeropuertos = aeropuertoRepositorio.obtenerTodosConCiudad().stream()
                .map(this::mapearAeropuerto)
                .toList();
        final List<VueloInstanciaResponse> vuelosInstancia = generarVuelosInstancia(fechaInicio, fechaFin).stream()
                .map(this::mapearVueloInstancia)
                .toList();

        return SimulacionInicioResponse.builder()
                .withSessionId(estado.getSessionId())
                .withEstado(estado.getEstado())
                .withMensaje(estado.getMensaje())
                .withFechaInicio(fechaInicio.toString())
                .withTotalDias(totalDias)
                .withAeropuertos(aeropuertos)
                .withVuelosInstancia(vuelosInstancia)
                .build();
    }

    private List<VueloInstancia> generarVuelosInstancia(final LocalDate fechaInicio, final LocalDate fechaFin) {
        final List<VueloProgramado> programados = vueloProgramadoRepositorio.obtenerTodos();
        return GeneradorVueloInstancias.generar(programados, fechaInicio, fechaFin);
    }

    private AeropuertoResponse mapearAeropuerto(final Aeropuerto aeropuerto) {
        return AeropuertoResponse.builder()
                .withIdAeropuerto(aeropuerto.getIdAeropuerto())
                .withCiudad(aeropuerto.getCiudad())
                .withCapacidadAlmacen(aeropuerto.getCapacidadAlmacen())
                .withMaletasActuales(aeropuerto.getMaletasActuales())
                .withLatitud(aeropuerto.getLatitud())
                .withLongitud(aeropuerto.getLongitud())
                .withHusoGMT(aeropuerto.getHusoGMT())
                .build();
    }

    private VueloInstanciaResponse mapearVueloInstancia(final VueloInstancia vueloInstancia) {
        final int capacidadDisponible = vueloInstancia.getCapacidadDisponible();
        final int capacidadMaxima = vueloInstancia.getCapacidadMaxima();
        return VueloInstanciaResponse.builder()
                .withIdVueloInstancia(vueloInstancia.getIdVueloInstancia())
                .withCodigo(vueloInstancia.getCodigo())
                .withAeropuertoOrigen(vueloInstancia.getAeropuertoOrigen() != null
                        ? vueloInstancia.getAeropuertoOrigen().getIdAeropuerto() : null)
                .withAeropuertoDestino(vueloInstancia.getAeropuertoDestino() != null
                        ? vueloInstancia.getAeropuertoDestino().getIdAeropuerto() : null)
                .withFechaSalida(vueloInstancia.getFechaSalida() != null ? vueloInstancia.getFechaSalida().toString() : null)
                .withFechaLlegada(vueloInstancia.getFechaLlegada() != null ? vueloInstancia.getFechaLlegada().toString() : null)
                .withCapacidadMaxima(capacidadMaxima)
                .withCapacidadDisponible(capacidadDisponible)
                .withCapacidadUsada(Math.max(0, capacidadMaxima - capacidadDisponible))
                .withEstado(vueloInstancia.getEstado() != null ? vueloInstancia.getEstado().name() : null)
                .build();
    }
}
