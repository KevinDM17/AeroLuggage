package pe.edu.pucp.aeroluggage.cargador;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;

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
            final LocalDateTime registroUtc = LocalDateTime.of(fecha, LocalTime.of(hora, minuto))
                    .minusHours(origen.getHusoGMT());

            final Pedido pedido = new Pedido(idEnvio, origen, destino, registroUtc, cantidad,
                    EstadoPedido.REGISTRADO.name());
            pedido.registrarPedido();
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

    private static final String ESTADO_PEDIDO_REGISTRADO = "REGISTRADO";
    private static final String ESTADO_MALETA_PENDIENTE = "PENDIENTE";
    private static final int INDICE_INICIO_FECHA_ENVIO = 10;
    private static final int INDICE_FIN_FECHA_ENVIO = 18;
    private static final int BYTES_MUESTRA_REGISTRO = 256;
    private static final Pattern ARCHIVO_ENVIO = Pattern.compile("^_envios_(\\w+)_\\.txt$");

    public static DatosEntrada cargarEnviosEnRango(final Path carpetaEnvios,
                                                   final Map<String, Aeropuerto> aeropuertos,
                                                   final LocalDate fechaInicio,
                                                   final LocalDate fechaFin) {
        final ArrayList<Pedido> pedidos = new ArrayList<>();
        final ArrayList<Maleta> maletas = new ArrayList<>();
        if (fechaInicio == null || fechaFin == null || fechaFin.isBefore(fechaInicio)) {
            return new DatosEntrada(pedidos, maletas);
        }

        try (LectorLotesEnvios lector = new LectorLotesEnvios(carpetaEnvios, aeropuertos, fechaInicio)) {
            while (!lector.pendientes.isEmpty()) {
                final RegistroEnvio registro = lector.pendientes.poll();
                if (registro == null) {
                    continue;
                }

                final LocalDate fechaRegistro = registro.getFechaRegistro().toLocalDate();
                registro.getFuente().leerSiguiente(aeropuertos, lector.pendientes);

                if (fechaRegistro.isBefore(fechaInicio)) {
                    continue;
                }
                if (fechaRegistro.isAfter(fechaFin)) {
                    break;
                }

                final Pedido pedido = registro.crearPedido();
                pedidos.add(pedido);
                agregarMaletas(pedido, pedido.getCantidadMaletas(), pedido.getFechaRegistro(), maletas);
            }
        }

        ordenarPorFecha(maletas);
        ordenarPedidosPorFecha(pedidos);
        return new DatosEntrada(pedidos, maletas);
    }

    public static LectorLotesEnvios crearLectorLotesEnvios(final Path carpetaEnvios,
                                                           final Map<String, Aeropuerto> aeropuertos) {
        return new LectorLotesEnvios(carpetaEnvios, aeropuertos);
    }

    public static LectorLotesEnvios crearLectorLotesEnvios(final Path carpetaEnvios,
                                                           final Map<String, Aeropuerto> aeropuertos,
                                                           final LocalDate fechaInicio) {
        return new LectorLotesEnvios(carpetaEnvios, aeropuertos, fechaInicio);
    }

    private static List<Path> listarArchivosEnvio(final Path carpetaEnvios) {
        try (Stream<Path> stream = Files.list(carpetaEnvios)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> ARCHIVO_ENVIO.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        } catch (final IOException exception) {
            throw new IllegalStateException("No se pudo leer la carpeta de envios: " + carpetaEnvios, exception);
        }
    }

    private static String extraerCodigoOrigen(final Path archivo) {
        final Matcher matcher = ARCHIVO_ENVIO.matcher(archivo.getFileName().toString());
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(1);
    }

    private static void agregarMaletas(final Pedido pedido, final int cantidadMaletas,
                                       final LocalDateTime fechaRegistro, final ArrayList<Maleta> maletas) {
        for (int i = 1; i <= cantidadMaletas; i++) {
            final String idMaleta = "MAL-" + pedido.getIdPedido() + "-" + String.format("%03d", i);
            maletas.add(new Maleta(idMaleta, pedido, fechaRegistro, null, ESTADO_MALETA_PENDIENTE));
        }
    }

    private static void ordenarPorFecha(final ArrayList<Maleta> maletas) {
        maletas.sort(Comparator.comparing(Maleta::getFechaRegistro));
    }

    private static void ordenarPedidosPorFecha(final ArrayList<Pedido> pedidos) {
        pedidos.sort(Comparator.comparing(Pedido::getFechaRegistro));
    }

    private static void registrarLineaInvalida(final String tipo, final String linea, final String motivo) {
        System.err.println("[CargaDatos] Linea de " + tipo + " ignorada: " + motivo + " | " + linea);
    }

    public static final class LectorLotesEnvios implements AutoCloseable {
        private final Map<String, Aeropuerto> aeropuertos;
        private final PriorityQueue<RegistroEnvio> pendientes;
        private final ArrayList<FuenteEnvio> fuentes;
        private final LocalDate fechaInicio;

        private LectorLotesEnvios(final Path carpetaEnvios, final Map<String, Aeropuerto> aeropuertos) {
            this(carpetaEnvios, aeropuertos, null);
        }

        private LectorLotesEnvios(final Path carpetaEnvios, final Map<String, Aeropuerto> aeropuertos,
                                  final LocalDate fechaInicio) {
            this.aeropuertos = aeropuertos;
            this.pendientes = new PriorityQueue<>(
                    Comparator.comparing(RegistroEnvio::getFechaRegistro)
                            .thenComparing(RegistroEnvio::getIdPedido)
            );
            this.fuentes = new ArrayList<>();
            this.fechaInicio = fechaInicio;
            inicializarFuentes(carpetaEnvios);
        }

        public DatosEntrada siguienteLote(final int cantidadPedidos) {
            final ArrayList<Pedido> pedidos = new ArrayList<>();
            final ArrayList<Maleta> maletas = new ArrayList<>();
            while (pedidos.size() < cantidadPedidos && !pendientes.isEmpty()) {
                final RegistroEnvio registro = pendientes.poll();
                final Pedido pedido = registro.crearPedido();
                pedidos.add(pedido);
                agregarMaletas(pedido, pedido.getCantidadMaletas(), pedido.getFechaRegistro(), maletas);
                registro.getFuente().leerSiguiente(aeropuertos, pendientes);
            }
            ordenarPedidosPorFecha(pedidos);
            ordenarPorFecha(maletas);
            return new DatosEntrada(pedidos, maletas);
        }

        public boolean tienePendientes() {
            return !pendientes.isEmpty();
        }

        @Override
        public void close() {
            for (final FuenteEnvio fuente : fuentes) {
                fuente.close();
            }
        }

        private void inicializarFuentes(final Path carpetaEnvios) {
            final List<Path> archivos = listarArchivosEnvio(carpetaEnvios);
            for (final Path archivo : archivos) {
                final String codigo = extraerCodigoOrigen(archivo);
                final Aeropuerto aeropuerto = aeropuertos.get(codigo);
                final int husoGMT = aeropuerto != null ? aeropuerto.getHusoGMT() : 0;
                final FuenteEnvio fuente = FuenteEnvio.abrir(archivo, husoGMT);
                fuentes.add(fuente);
                if (fechaInicio != null) {
                    fuente.posicionarEnFecha(fechaInicio);
                }
                fuente.leerSiguiente(aeropuertos, pendientes);
            }
        }
    }

    private static final class FuenteEnvio implements AutoCloseable {
        private final String codigoOrigen;
        private final RandomAccessFile reader;
        private final long bytesPorRegistro;
        private final int husoGMT;

        private FuenteEnvio(final String codigoOrigen, final RandomAccessFile reader,
                            final long bytesPorRegistro, final int husoGMT) {
            this.codigoOrigen = codigoOrigen;
            this.reader = reader;
            this.bytesPorRegistro = bytesPorRegistro;
            this.husoGMT = husoGMT;
        }

        static FuenteEnvio abrir(final Path archivo, final int husoGMT) {
            try {
                final RandomAccessFile reader = new RandomAccessFile(archivo.toFile(), "r");
                return new FuenteEnvio(
                        extraerCodigoOrigen(archivo),
                        reader,
                        detectarBytesPorRegistro(reader),
                        husoGMT
                );
            } catch (final IOException exception) {
                throw new IllegalStateException("No se pudo abrir el archivo de envios: " + archivo, exception);
            }
        }

        void posicionarEnFecha(final LocalDate fechaInicio) {
            if (fechaInicio == null) {
                return;
            }
            try {
                final LocalDate localFechaInicio = fechaInicio.atStartOfDay()
                        .plusHours(husoGMT).toLocalDate();
                final long posicion = buscarPrimeraPosicionDesdeFecha(localFechaInicio);
                reader.seek(posicion);
            } catch (final IOException exception) {
                throw new IllegalStateException(
                        "No se pudo posicionar el archivo de envios de " + codigoOrigen + " en la fecha "
                                + fechaInicio,
                        exception
                );
            }
        }

        void leerSiguiente(final Map<String, Aeropuerto> aeropuertos,
                           final PriorityQueue<RegistroEnvio> pendientes) {
            try {
                String linea = reader.readLine();
                while (linea != null) {
                    final RegistroEnvio registro = RegistroEnvio.desdeLinea(linea, codigoOrigen, aeropuertos, this);
                    if (registro != null) {
                        pendientes.add(registro);
                        return;
                    }
                    linea = reader.readLine();
                }
            } catch (final IOException exception) {
                throw new IllegalStateException("No se pudo leer el archivo de envios de " + codigoOrigen, exception);
            }
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (final IOException exception) {
                throw new IllegalStateException(
                        "No se pudo cerrar el archivo de envios de " + codigoOrigen, exception);
            }
        }

        private long buscarPrimeraPosicionDesdeFecha(final LocalDate fechaObjetivo) throws IOException {
            if (bytesPorRegistro > 0L) {
                return buscarPrimeraPosicionDesdeFechaRegistroFijo(fechaObjetivo);
            }
            return buscarPrimeraPosicionDesdeFechaLineal(fechaObjetivo);
        }

        private long buscarPrimeraPosicionDesdeFechaRegistroFijo(final LocalDate fechaObjetivo)
                throws IOException {
            final long totalRegistros = reader.length() / bytesPorRegistro;
            long izquierda = 0L;
            long derecha = totalRegistros;
            while (izquierda < derecha) {
                final long mitad = (izquierda + derecha) >>> 1;
                final LocalDate fechaLinea = leerFechaRegistro(mitad);
                if (fechaLinea == null || fechaLinea.isBefore(fechaObjetivo)) {
                    izquierda = mitad + 1L;
                    continue;
                }
                derecha = mitad;
            }
            return Math.min(izquierda * bytesPorRegistro, reader.length());
        }

        private LocalDate leerFechaRegistro(final long indiceRegistro) throws IOException {
            reader.seek(indiceRegistro * bytesPorRegistro);
            return extraerFechaLinea(reader.readLine());
        }

        private long buscarPrimeraPosicionDesdeFechaLineal(final LocalDate fechaObjetivo) throws IOException {
            final long longitud = reader.length();
            if (longitud <= 0L) {
                return 0L;
            }

            long izquierda = 0L;
            long derecha = longitud;
            while (izquierda < derecha) {
                final long mitad = (izquierda + derecha) >>> 1;
                final long inicioLinea = posicionarEnInicioLinea(mitad, longitud);
                if (inicioLinea >= longitud) {
                    derecha = mitad;
                    continue;
                }

                reader.seek(inicioLinea);
                final String linea = reader.readLine();
                final LocalDate fechaLinea = extraerFechaLinea(linea);
                if (fechaLinea == null) {
                    final long siguiente = reader.getFilePointer();
                    if (siguiente <= inicioLinea) {
                        break;
                    }
                    izquierda = siguiente;
                    continue;
                }

                if (fechaLinea.isBefore(fechaObjetivo)) {
                    final long siguiente = reader.getFilePointer();
                    if (siguiente <= inicioLinea) {
                        break;
                    }
                    izquierda = siguiente;
                    continue;
                }

                derecha = inicioLinea;
            }
            return normalizarPosicionInicial(izquierda, longitud);
        }

        private long posicionarEnInicioLinea(final long posicion, final long longitud) throws IOException {
            if (posicion <= 0L) {
                return 0L;
            }
            if (posicion >= longitud) {
                return longitud;
            }
            reader.seek(posicion - 1L);
            int actual;
            while (reader.getFilePointer() < longitud) {
                actual = reader.read();
                if (actual == '\n') {
                    return reader.getFilePointer();
                }
                final long actualPosicion = reader.getFilePointer();
                if (actualPosicion <= 1L) {
                    return 0L;
                }
                reader.seek(actualPosicion - 2L);
            }
            return longitud;
        }

        private long normalizarPosicionInicial(final long posicion, final long longitud) throws IOException {
            long inicioLinea = posicionarEnInicioLinea(posicion, longitud);
            reader.seek(inicioLinea);
            String linea = reader.readLine();
            while (linea != null) {
                final LocalDate fecha = extraerFechaLinea(linea);
                if (fecha != null) {
                    return inicioLinea;
                }
                final long siguiente = reader.getFilePointer();
                if (siguiente <= inicioLinea) {
                    return 0L;
                }
                inicioLinea = siguiente;
                reader.seek(siguiente);
                linea = reader.readLine();
            }
            return longitud;
        }

        private LocalDate extraerFechaLinea(final String linea) {
            if (linea == null || linea.isBlank()) {
                return null;
            }
            if (linea.length() >= INDICE_FIN_FECHA_ENVIO) {
                return extraerFechaPorPosicion(linea);
            }
            final String[] partes = linea.trim().split("-");
            if (partes.length != 7) {
                return null;
            }
            try {
                return LocalDate.parse(partes[1].trim(), FORMATO_FECHA);
            } catch (final RuntimeException exception) {
                return null;
            }
        }

        private LocalDate extraerFechaPorPosicion(final String linea) {
            try {
                final String fecha = linea.substring(INDICE_INICIO_FECHA_ENVIO, INDICE_FIN_FECHA_ENVIO);
                return LocalDate.parse(fecha, FORMATO_FECHA);
            } catch (final RuntimeException exception) {
                return null;
            }
        }

        private static long detectarBytesPorRegistro(final RandomAccessFile reader) throws IOException {
            final long longitud = reader.length();
            if (longitud <= 0L) {
                return 0L;
            }
            reader.seek(0L);
            final int limite = (int) Math.min(longitud, BYTES_MUESTRA_REGISTRO);
            for (int i = 0; i < limite; i++) {
                final int actual = reader.read();
                if (actual == '\n') {
                    final long bytesRegistro = i + 1L;
                    return longitud % bytesRegistro == 0L ? bytesRegistro : 0L;
                }
            }
            return 0L;
        }
    }

    private static final class RegistroEnvio {
        private final String idPedido;
        private final Aeropuerto origen;
        private final Aeropuerto destino;
        private final LocalDateTime fechaRegistro;
        private final int cantidadMaletas;
        private final FuenteEnvio fuente;

        private RegistroEnvio(final String idPedido, final Aeropuerto origen, final Aeropuerto destino,
                              final LocalDateTime fechaRegistro, final int cantidadMaletas,
                              final FuenteEnvio fuente) {
            this.idPedido = idPedido;
            this.origen = origen;
            this.destino = destino;
            this.fechaRegistro = fechaRegistro;
            this.cantidadMaletas = cantidadMaletas;
            this.fuente = fuente;
        }

        static RegistroEnvio desdeLinea(final String linea, final String codigoOrigen,
                                        final Map<String, Aeropuerto> aeropuertos, final FuenteEnvio fuente) {
            try {
                final String[] partes = linea.trim().split("-");
                if (partes.length != 7) {
                    registrarLineaInvalida("envio", linea,
                            "formato esperado ID-AAAAMMDD-HH-MM-DEST-CANT-CLIENTE");
                    return null;
                }
                final Aeropuerto origen = aeropuertos.get(codigoOrigen);
                final Aeropuerto destino = aeropuertos.get(partes[4].trim());
                if (origen == null || destino == null) {
                    registrarLineaInvalida("envio", linea, "aeropuerto origen o destino no existe");
                    return null;
                }
                final LocalDate fecha = LocalDate.parse(partes[1].trim(), FORMATO_FECHA);
                final int hora = Integer.parseInt(partes[2].trim());
                final int minuto = Integer.parseInt(partes[3].trim());
                final int cantidadMaletas = Integer.parseInt(partes[5].trim());
                if (cantidadMaletas <= 0) {
                    registrarLineaInvalida("envio", linea, "cantidad de maletas debe ser mayor que cero");
                    return null;
                }
                final LocalDateTime fechaRegistro = LocalDateTime.of(fecha, LocalTime.of(hora, minuto))
                        .minusHours(origen.getHusoGMT());
                final String idPedido = codigoOrigen + "-" + partes[0].trim();
                return new RegistroEnvio(
                        idPedido,
                        origen,
                        destino,
                        fechaRegistro,
                        cantidadMaletas,
                        fuente
                );
            } catch (final RuntimeException exception) {
                registrarLineaInvalida("envio", linea, exception.getMessage());
                return null;
            }
        }

        Pedido crearPedido() {
            final Pedido pedido = new Pedido(
                    idPedido,
                    origen,
                    destino,
                    fechaRegistro,
                    cantidadMaletas,
                    ESTADO_PEDIDO_REGISTRADO
            );
            pedido.registrarPedido();
            return pedido;
        }

        String getIdPedido() {
            return idPedido;
        }

        LocalDateTime getFechaRegistro() {
            return fechaRegistro;
        }

        FuenteEnvio getFuente() {
            return fuente;
        }
    }
}
