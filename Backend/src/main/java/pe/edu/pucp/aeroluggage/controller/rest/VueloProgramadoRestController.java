package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloProgramadoRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloProgramadoResponse;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;
import pe.edu.pucp.aeroluggage.servicios.ServicioVueloProgramado;
import pe.edu.pucp.aeroluggage.simulacion.OperacionesDiaADiaService;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/vuelos-programados")
@RequiredArgsConstructor
public class VueloProgramadoRestController {

    private final ServicioVueloProgramado servicioVueloProgramado;
    private final AeropuertoRepositorio aeropuertoRepositorio;

    @Lazy
    @Autowired
    private OperacionesDiaADiaService operacionesService;

    @GetMapping
    public List<VueloProgramadoResponse> listar(
            @RequestParam(name = "airport", required = false) final String aeropuerto) {
        final List<VueloProgramado> vuelos = (aeropuerto != null && !aeropuerto.isBlank())
                ? servicioVueloProgramado.listarPorAeropuerto(aeropuerto)
                : servicioVueloProgramado.listarTodos();
        return vuelos.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public VueloProgramadoResponse obtener(@PathVariable final String id) {
        return servicioVueloProgramado.obtenerPorId(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vuelo programado no encontrado: " + id));
    }

    @PostMapping
    public VueloProgramadoResponse crear(@RequestBody final VueloProgramadoRequest request) {
        final VueloProgramado vuelo = toEntity(request);
        final VueloProgramado creado = servicioVueloProgramado.crear(vuelo);
        if (operacionesService != null) {
            try {
                operacionesService.onVueloProgramadoCreado(creado);
            } catch (final Exception e) {
                log.warn("[AeroLuggage/VueloProgramadoRestController] "
                        + "sync onVueloProgramadoCreado failed: {}", e.getMessage());
            }
        }
        return toResponse(creado);
    }

    @PutMapping("/{id}")
    public VueloProgramadoResponse actualizar(@PathVariable final String id,
                                               @RequestBody final VueloProgramadoRequest request) {
        final VueloProgramado vuelo = toEntity(request);
        final Optional<VueloProgramado> actualizado = servicioVueloProgramado.actualizar(id, vuelo);
        final VueloProgramadoResponse response = actualizado
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vuelo programado no encontrado: " + id));
        if (operacionesService != null) {
            try {
                operacionesService.onVueloProgramadoActualizado(id, vuelo);
            } catch (final Exception e) {
                log.warn("[AeroLuggage/VueloProgramadoRestController] "
                        + "sync onVueloProgramadoActualizado failed: {}", e.getMessage());
            }
        }
        return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable final String id) {
        final boolean eliminado = servicioVueloProgramado.eliminar(id);
        if (!eliminado) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Vuelo programado no encontrado: " + id);
        }
        if (operacionesService != null) {
            try {
                operacionesService.onVueloProgramadoEliminado(id);
            } catch (final Exception e) {
                log.warn("[AeroLuggage/VueloProgramadoRestController] "
                        + "sync onVueloProgramadoEliminado failed: {}", e.getMessage());
            }
        }
        return ResponseEntity.noContent().build();
    }

    private VueloProgramado toEntity(final VueloProgramadoRequest request) {
        final VueloProgramado vuelo = new VueloProgramado();
        if (request.getIdVueloProgramado() != null && !request.getIdVueloProgramado().isBlank()) {
            vuelo.setIdVueloProgramado(request.getIdVueloProgramado());
        } else {
            vuelo.setIdVueloProgramado(request.getCodigo());
        }
        vuelo.setCodigo(request.getCodigo());
        try {
            vuelo.setHoraSalida(request.getHoraSalida() != null && !request.getHoraSalida().isBlank()
                    ? LocalTime.parse(request.getHoraSalida()) : null);
        } catch (final DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de hora de salida invalido. Use HH:mm");
        }
        try {
            vuelo.setHoraLlegada(request.getHoraLlegada() != null && !request.getHoraLlegada().isBlank()
                    ? LocalTime.parse(request.getHoraLlegada()) : null);
        } catch (final DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de hora de llegada invalido. Use HH:mm");
        }
        vuelo.setCapacidadBase(request.getCapacidadBase());
        if (request.getIdAeropuertoOrigen() != null && !request.getIdAeropuertoOrigen().isBlank()) {
            final Aeropuerto origen = new Aeropuerto();
            origen.setIdAeropuerto(request.getIdAeropuertoOrigen());
            vuelo.setAeropuertoOrigen(origen);
        }
        if (request.getIdAeropuertoDestino() != null && !request.getIdAeropuertoDestino().isBlank()) {
            final Aeropuerto destino = new Aeropuerto();
            destino.setIdAeropuerto(request.getIdAeropuertoDestino());
            vuelo.setAeropuertoDestino(destino);
        }
        return vuelo;
    }

    private VueloProgramadoResponse toResponse(final VueloProgramado vuelo) {
        final Aeropuerto origen = vuelo.getAeropuertoOrigen();
        final Aeropuerto destino = vuelo.getAeropuertoDestino();
        final Ciudad ciudadDestino = destino != null ? destino.getCiudad() : null;
        return VueloProgramadoResponse.builder()
                .withIdVueloProgramado(vuelo.getIdVueloProgramado())
                .withCodigo(vuelo.getCodigo())
                .withHoraSalida(vuelo.getHoraSalida() != null ? vuelo.getHoraSalida().toString() : null)
                .withHoraLlegada(vuelo.getHoraLlegada() != null ? vuelo.getHoraLlegada().toString() : null)
                .withCapacidadBase(vuelo.getCapacidadBase())
                .withIdAeropuertoOrigen(origen != null ? origen.getIdAeropuerto() : null)
                .withIdAeropuertoDestino(destino != null ? destino.getIdAeropuerto() : null)
                .withNombreCiudadDestino(ciudadDestino != null ? ciudadDestino.getNombre() : null)
                .withContinenteDestino(ciudadDestino != null && ciudadDestino.getContinente() != null
                        ? ciudadDestino.getContinente().name() : null)
                .withHusoOrigen(origen != null ? origen.getHusoGMT() : 0)
                .withHusoDestino(destino != null ? destino.getHusoGMT() : 0)
                .build();
    }
}
