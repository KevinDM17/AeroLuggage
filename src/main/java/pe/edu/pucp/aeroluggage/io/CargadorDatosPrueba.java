package pe.edu.pucp.aeroluggage.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.servicios.GeneradorVuelosInstancia;

public final class CargadorDatosPrueba {
    public static final int DIAS_VUELOS_REPETIDOS = 7;

    private static final int MALETAS_ACTUALES_INICIALES = 0;
    private static final String ESTADO_PEDIDO_REGISTRADO = "REGISTRADO";
    private static final String ESTADO_MALETA_PENDIENTE = "PENDIENTE";
    private static final DateTimeFormatter FORMATO_FECHA_ENVIO = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern COORDENADAS = Pattern.compile(
            ".*Latitude:\\D*(\\d+)\\D+(\\d+)\\D+(\\d+)\\D*([NS])"
                    + ".*Longitude:\\D*(\\d+)\\D+(\\d+)\\D+(\\d+)\\D*([EW]).*$"
    );
    private static final Pattern ARCHIVO_ENVIO = Pattern.compile("^_envios_(\\w+)_\\.txt$");

    private CargadorDatosPrueba() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static InstanciaProblema cargarDesdeDocumentos(final Path carpetaDocumentos) {
        final Path archivoAeropuertos = carpetaDocumentos.resolve("Aeropuertos.txt");
        final Path archivoVuelos = carpetaDocumentos.resolve("planes_vuelo.txt");
        final Path carpetaEnvios = carpetaDocumentos.resolve("Envios");

        final ArrayList<Aeropuerto> aeropuertos = cargarAeropuertos(archivoAeropuertos);
        final Map<String, Aeropuerto> aeropuertosPorCodigo = indexarAeropuertos(aeropuertos);
        final DatosEntrada datosEntrada = cargarEnvios(carpetaEnvios, aeropuertosPorCodigo);
        final LocalDate fechaInicioVuelos = obtenerFechaInicio(datosEntrada.getMaletas());
        final ArrayList<VueloProgramado> vuelosProgramados = cargarVuelosProgramados(
                archivoVuelos,
                aeropuertosPorCodigo,
                fechaInicioVuelos,
                DIAS_VUELOS_REPETIDOS
        );
        final ArrayList<VueloInstancia> vuelosInstancia = GeneradorVuelosInstancia.generar(
                vuelosProgramados,
                fechaInicioVuelos,
                DIAS_VUELOS_REPETIDOS
        );

        return new InstanciaProblema("PRUEBA-TXT", datosEntrada.getMaletas(), vuelosProgramados, vuelosInstancia,
                aeropuertos);
    }

    public static ArrayList<Aeropuerto> cargarAeropuertos(final Path archivoAeropuertos) {
        final ArrayList<Aeropuerto> aeropuertos = new ArrayList<>();
        Continente continenteActual = null;
        for (final String linea : leerLineas(archivoAeropuertos)) {
            continenteActual = detectarContinente(linea, continenteActual);
            final Aeropuerto aeropuerto = crearAeropuertoDesdeLinea(linea, continenteActual);
            if (aeropuerto == null) {
                continue;
            }
            aeropuertos.add(aeropuerto);
        }
        return aeropuertos;
    }

    public static DatosEntrada cargarEnvios(final Path carpetaEnvios, final Map<String, Aeropuerto> aeropuertos) {
        final ArrayList<Pedido> pedidos = new ArrayList<>();
        final ArrayList<Maleta> maletas = new ArrayList<>();
        final List<Path> archivos = listarArchivosEnvio(carpetaEnvios);
        for (final Path archivo : archivos) {
            final String codigoOrigen = extraerCodigoOrigen(archivo);
            final Aeropuerto origen = aeropuertos.get(codigoOrigen);
            if (origen == null) {
                continue;
            }
            for (final String linea : leerLineas(archivo)) {
                final RegistroEnvio registro = RegistroEnvio.desdeLinea(linea, codigoOrigen, aeropuertos, null);
                if (registro == null) {
                    continue;
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

    public static ArrayList<VueloProgramado> cargarVuelosProgramados(final Path archivoVuelos,
                                                                     final Map<String, Aeropuerto> aeropuertos,
                                                                     final LocalDate fechaInicio,
                                                                     final int dias) {
        final ArrayList<VueloProgramado> vuelos = new ArrayList<>();
        int secuencia = 1;
        final List<String> lineas = leerLineas(archivoVuelos);
        for (final String linea : lineas) {
            final VueloProgramado vuelo = crearVueloProgramado(linea, aeropuertos, secuencia);
            if (vuelo == null) {
                continue;
            }
            vuelos.add(vuelo);
            secuencia++;
        }
        vuelos.sort(Comparator.comparing(VueloProgramado::getHoraSalida));
        return vuelos;
    }

    public static ArrayList<VueloInstancia> cargarVuelosInstancia(
            final Path archivoVuelos,
            final Map<String, Aeropuerto> aeropuertos,
            final LocalDate fechaInicio,
            final int dias
    ) {
        final ArrayList<VueloProgramado> vuelosProgramados = cargarVuelosProgramados(
                archivoVuelos,
                aeropuertos,
                fechaInicio,
                dias
        );
        return GeneradorVuelosInstancia.generar(vuelosProgramados, fechaInicio, dias);
    }

    public static Map<String, Aeropuerto> indexarAeropuertos(final ArrayList<Aeropuerto> aeropuertos) {
        final Map<String, Aeropuerto> indice = new HashMap<>();
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                continue;
            }
            indice.put(aeropuerto.getIdAeropuerto(), aeropuerto);
        }
        return indice;
    }

    private static VueloProgramado crearVueloProgramado(final String linea, final Map<String, Aeropuerto> aeropuertos,
                                                        final int secuencia) {
        try {
            final String[] partes = linea.trim().split("-");
            if (partes.length != 5) {
                registrarLineaInvalida("vuelo", linea, "formato esperado ORIGEN-DESTINO-HH:mm-HH:mm-CAPACIDAD");
                return null;
            }
            final Aeropuerto origen = aeropuertos.get(partes[0].trim());
            final Aeropuerto destino = aeropuertos.get(partes[1].trim());
            if (origen == null || destino == null) {
                registrarLineaInvalida("vuelo", linea, "aeropuerto origen o destino no existe");
                return null;
            }
            final LocalTime horaSalida = LocalTime.parse(partes[2].trim());
            final LocalTime horaLlegada = LocalTime.parse(partes[3].trim());
            final int capacidad = Integer.parseInt(partes[4].trim());
            if (capacidad <= 0) {
                registrarLineaInvalida("vuelo", linea, "capacidad debe ser mayor que cero");
                return null;
            }
            final String idVuelo = String.format(
                    "%s-%s-%06d",
                    origen.getIdAeropuerto(),
                    destino.getIdAeropuerto(),
                    secuencia
            );
            return new VueloProgramado(idVuelo, idVuelo, horaSalida, horaLlegada, capacidad, origen, destino);
        } catch (final RuntimeException exception) {
            registrarLineaInvalida("vuelo", linea, exception.getMessage());
            return null;
        }
    }

    private static void agregarMaletas(final Pedido pedido, final int cantidadMaletas,
                                       final LocalDateTime fechaRegistro, final ArrayList<Maleta> maletas) {
        for (int i = 1; i <= cantidadMaletas; i++) {
            final String idMaleta = "MAL-" + pedido.getIdPedido() + "-" + String.format("%03d", i);
            maletas.add(new Maleta(idMaleta, pedido, fechaRegistro, null, ESTADO_MALETA_PENDIENTE));
        }
    }

    private static Aeropuerto crearAeropuertoDesdeLinea(final String linea, final Continente continenteActual) {
        try {
            final String limpia = limpiarLinea(linea);
            if (!limpia.contains("Latitude:")) {
                return null;
            }
            final Continente continente = continenteActual == null ? Continente.AMERICA_DEL_SUR : continenteActual;
            final String[] secciones = limpia.split("Latitude:", 2);
            if (secciones.length < 2) {
                registrarLineaInvalida("aeropuerto", linea, "no contiene coordenadas completas");
                return null;
            }
            final String[] tokens = secciones[0].trim().split("\\s+");
            if (tokens.length < 7) {
                registrarLineaInvalida("aeropuerto", linea, "faltan columnas obligatorias");
                return null;
            }
            final String codigo = tokens[1];
            final String idCiudad = tokens[tokens.length - 3];
            final int husoHorario = Integer.parseInt(tokens[tokens.length - 2]);
            final int capacidad = Integer.parseInt(tokens[tokens.length - 1]);
            if (capacidad <= 0) {
                registrarLineaInvalida("aeropuerto", linea, "capacidad debe ser mayor que cero");
                return null;
            }
            final String nombreCiudad = unirTokens(tokens, 2, tokens.length - 3);
            float latitud = 0F;
            float longitud = 0F;
            final Matcher matcherCoordenadas = COORDENADAS.matcher(limpia);
            if (matcherCoordenadas.matches()) {
                latitud = convertirCoordenada(
                        matcherCoordenadas.group(1),
                        matcherCoordenadas.group(2),
                        matcherCoordenadas.group(3),
                        matcherCoordenadas.group(4)
                );
                longitud = convertirCoordenada(
                        matcherCoordenadas.group(5),
                        matcherCoordenadas.group(6),
                        matcherCoordenadas.group(7),
                        matcherCoordenadas.group(8)
                );
            }
            final Ciudad ciudad = new Ciudad(idCiudad, nombreCiudad, continente);
            return new Aeropuerto(codigo, ciudad, capacidad, MALETAS_ACTUALES_INICIALES, longitud, latitud,
                    husoHorario);
        } catch (final RuntimeException exception) {
            registrarLineaInvalida("aeropuerto", linea, exception.getMessage());
            return null;
        }
    }

    private static String unirTokens(final String[] tokens, final int inicio, final int finExclusivo) {
        final StringBuilder builder = new StringBuilder();
        for (int i = inicio; i < finExclusivo; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(tokens[i]);
        }
        return builder.toString();
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

    private static List<String> leerLineas(final Path archivo) {
        try {
            return Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            return leerLineasIso(archivo, exception);
        }
    }

    private static List<String> leerLineasIso(final Path archivo, final IOException excepcionUtf8) {
        try {
            return Files.readAllLines(archivo, StandardCharsets.UTF_16);
        } catch (final IOException exceptionUtf16) {
            exceptionUtf16.addSuppressed(excepcionUtf8);
            return leerLineasIsoFinal(archivo, exceptionUtf16);
        }
    }

    private static List<String> leerLineasIsoFinal(final Path archivo, final IOException excepcionPrevia) {
        try {
            return Files.readAllLines(archivo, StandardCharsets.ISO_8859_1);
        } catch (final IOException exception) {
            exception.addSuppressed(excepcionPrevia);
            throw new IllegalStateException("No se pudo leer el archivo: " + archivo, exception);
        }
    }

    private static String extraerCodigoOrigen(final Path archivo) {
        final Matcher matcher = ARCHIVO_ENVIO.matcher(archivo.getFileName().toString());
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(1);
    }

    private static LocalDate obtenerFechaInicio(final ArrayList<Maleta> maletas) {
        if (maletas == null || maletas.isEmpty()) {
            return LocalDate.now();
        }
        return maletas.get(0).getFechaRegistro().toLocalDate();
    }

    private static void ordenarPorFecha(final ArrayList<Maleta> maletas) {
        maletas.sort(Comparator.comparing(Maleta::getFechaRegistro));
    }

    private static void ordenarPedidosPorFecha(final ArrayList<Pedido> pedidos) {
        pedidos.sort(Comparator.comparing(Pedido::getFechaRegistro));
    }

    private static Continente detectarContinente(final String linea, final Continente actual) {
        final String normalizada = quitarTildes(linea).toLowerCase();
        if (normalizada.contains("america del sur")) {
            return Continente.AMERICA_DEL_SUR;
        }
        if (normalizada.contains("america del norte")) {
            return Continente.AMERICA_DEL_NORTE;
        }
        if (normalizada.contains("centro america")) {
            return Continente.CENTRO_AMERICA;
        }
        if (normalizada.contains("europa")) {
            return Continente.EUROPA;
        }
        if (normalizada.contains("asia")) {
            return Continente.ASIA;
        }
        if (normalizada.contains("oceania")) {
            return Continente.OCEANIA;
        }
        if (normalizada.contains("africa")) {
            return Continente.AFRICA;
        }
        return actual;
    }

    private static String limpiarLinea(final String linea) {
        return quitarTildes(linea)
                .replace('\uFEFF', ' ')
                .replace('\t', ' ');
    }

    private static String quitarTildes(final String valor) {
        return valor
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U");
    }

    private static float convertirCoordenada(final String grados, final String minutos,
                                             final String segundos, final String orientacion) {
        final float valor = Float.parseFloat(grados)
                + Float.parseFloat(minutos) / 60F
                + Float.parseFloat(segundos) / 3600F;
        if ("S".equals(orientacion) || "W".equals(orientacion)) {
            return -valor;
        }
        return valor;
    }

    private static void registrarLineaInvalida(final String tipo, final String linea, final String motivo) {
        System.err.println("[CargaDatos] Linea de " + tipo + " ignorada: " + motivo + " | " + linea);
    }

    public static final class LectorLotesEnvios implements AutoCloseable {
        private final Map<String, Aeropuerto> aeropuertos;
        private final PriorityQueue<RegistroEnvio> pendientes;
        private final ArrayList<FuenteEnvio> fuentes;

        private LectorLotesEnvios(final Path carpetaEnvios, final Map<String, Aeropuerto> aeropuertos) {
            this.aeropuertos = aeropuertos;
            this.pendientes = new PriorityQueue<>(
                    Comparator.comparing(RegistroEnvio::getFechaRegistro)
                            .thenComparing(RegistroEnvio::getIdPedido)
            );
            this.fuentes = new ArrayList<>();
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
                final FuenteEnvio fuente = FuenteEnvio.abrir(archivo);
                fuentes.add(fuente);
                fuente.leerSiguiente(aeropuertos, pendientes);
            }
        }
    }

    private static final class FuenteEnvio implements AutoCloseable {
        private final String codigoOrigen;
        private final BufferedReader reader;

        private FuenteEnvio(final String codigoOrigen, final BufferedReader reader) {
            this.codigoOrigen = codigoOrigen;
            this.reader = reader;
        }

        static FuenteEnvio abrir(final Path archivo) {
            try {
                return new FuenteEnvio(
                        extraerCodigoOrigen(archivo),
                        Files.newBufferedReader(archivo, StandardCharsets.UTF_8)
                );
            } catch (final IOException exception) {
                throw new IllegalStateException("No se pudo abrir el archivo de envios: " + archivo, exception);
            }
        }

        void leerSiguiente(final Map<String, Aeropuerto> aeropuertos, final PriorityQueue<RegistroEnvio> pendientes) {
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
                throw new IllegalStateException("No se pudo cerrar el archivo de envios de " + codigoOrigen, exception);
            }
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
                              final LocalDateTime fechaRegistro, final int cantidadMaletas, final FuenteEnvio fuente) {
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
                    registrarLineaInvalida("envio", linea, "formato esperado ID-AAAAMMDD-HH-MM-DEST-CANT-CLIENTE");
                    return null;
                }
                final Aeropuerto origen = aeropuertos.get(codigoOrigen);
                final Aeropuerto destino = aeropuertos.get(partes[4].trim());
                if (origen == null || destino == null) {
                    registrarLineaInvalida("envio", linea, "aeropuerto origen o destino no existe");
                    return null;
                }
                final LocalDate fecha = LocalDate.parse(partes[1].trim(), FORMATO_FECHA_ENVIO);
                final int hora = Integer.parseInt(partes[2].trim());
                final int minuto = Integer.parseInt(partes[3].trim());
                final int cantidadMaletas = Integer.parseInt(partes[5].trim());
                if (cantidadMaletas <= 0) {
                    registrarLineaInvalida("envio", linea, "cantidad de maletas debe ser mayor que cero");
                    return null;
                }
                final LocalDateTime fechaRegistro = LocalDateTime.of(fecha, LocalTime.of(hora, minuto));
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
