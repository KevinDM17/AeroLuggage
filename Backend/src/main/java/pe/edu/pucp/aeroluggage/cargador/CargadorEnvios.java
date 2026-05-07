package pe.edu.pucp.aeroluggage.cargador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;

public final class CargadorEnvios {

    private static final Pattern NOMBRE_ARCHIVO = Pattern.compile("^_envios_([A-Z]{4})_.*\\.txt$");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final class ResultadoCarga {
        private final List<Pedido> pedidos;
        private final List<Maleta> maletas;

        public ResultadoCarga(final List<Pedido> pedidos, final List<Maleta> maletas) {
            this.pedidos = pedidos;
            this.maletas = maletas;
        }

        public List<Pedido> getPedidos() {
            return pedidos;
        }

        public List<Maleta> getMaletas() {
            return maletas;
        }
    }

    private CargadorEnvios() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static ResultadoCarga cargarDesdeDirectorio(final Path directorio,
                                                      final Map<String, Aeropuerto> aeropuertosPorIcao) {
        final List<Pedido> pedidos = new ArrayList<>();
        final List<Maleta> maletas = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directorio, "_envios_*.txt")) {
            for (final Path archivo : stream) {
                final Matcher matcher = NOMBRE_ARCHIVO.matcher(archivo.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                final String icaoOrigen = matcher.group(1);
                final Aeropuerto origen = aeropuertosPorIcao.get(icaoOrigen);
                if (origen == null) {
                    continue;
                }
                cargarArchivo(archivo, origen, aeropuertosPorIcao, pedidos, maletas);
            }
        } catch (final IOException ex) {
            throw new IllegalStateException("No se pudo leer el directorio de envios: " + directorio, ex);
        }
        return new ResultadoCarga(pedidos, maletas);
    }

    public static ResultadoCarga cargarArchivo(final Path archivo, final Aeropuerto origen,
                                               final Map<String, Aeropuerto> aeropuertosPorIcao) {
        final List<Pedido> pedidos = new ArrayList<>();
        final List<Maleta> maletas = new ArrayList<>();
        cargarArchivo(archivo, origen, aeropuertosPorIcao, pedidos, maletas);
        return new ResultadoCarga(pedidos, maletas);
    }

    private static void cargarArchivo(final Path archivo, final Aeropuerto origen,
                                      final Map<String, Aeropuerto> aeropuertosPorIcao,
                                      final List<Pedido> pedidos, final List<Maleta> maletas) {
        final List<String> lineas = leerLineas(archivo);
        for (final String lineaCruda : lineas) {
            final String linea = lineaCruda.trim();
            if (linea.isEmpty()) {
                continue;
            }
            final String[] partes = linea.split("-");
            if (partes.length != 7) {
                continue;
            }
            final String idEnvio = partes[0].trim();
            final LocalDate fecha = LocalDate.parse(partes[1].trim(), FORMATO_FECHA);
            final int hora = Integer.parseInt(partes[2].trim());
            final int minuto = Integer.parseInt(partes[3].trim());
            final String icaoDestino = partes[4].trim();
            final int cantidad = Integer.parseInt(partes[5].trim());
            final String idCliente = partes[6].trim();

            final Aeropuerto destino = aeropuertosPorIcao.get(icaoDestino);
            if (destino == null) {
                continue;
            }
            final LocalDateTime registroLocal = LocalDateTime.of(fecha, LocalTime.of(hora, minuto));
            final LocalDateTime registroUtc = registroLocal.minusHours(origen.getHusoGMT());
            final double plazoDias = Ruta.calcularPlazo(origen, destino);
            final LocalDateTime fechaHoraPlazo = registroUtc.plusMinutes((long) Math.round(plazoDias * 24 * 60));

            final Pedido pedido = new Pedido(idEnvio, origen, destino, registroUtc, fechaHoraPlazo,
                    cantidad, EstadoPedido.REGISTRADO);
            pedidos.add(pedido);

            for (int i = 1; i <= cantidad; i++) {
                final String idMaleta = idEnvio + "-" + idCliente + "-" + String.format("%03d", i);
                maletas.add(new Maleta(idMaleta, pedido, registroUtc, null, EstadoMaleta.EN_ALMACEN));
            }
        }
    }

    private static List<String> leerLineas(final Path archivo) {
        try {
            return Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo de envios: " + archivo, ex);
        }
    }
}
