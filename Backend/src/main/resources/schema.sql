CREATE TABLE IF NOT EXISTS ciudad (
    id_ciudad   TEXT NOT NULL,
    nombre      TEXT NOT NULL,
    continente  TEXT NOT NULL,
    PRIMARY KEY (id_ciudad)
);

CREATE TABLE IF NOT EXISTS aeropuerto (
    id_aeropuerto      TEXT    NOT NULL,
    id_ciudad          TEXT    NOT NULL,
    capacidad_almacen  INTEGER NOT NULL,
    maletas_actuales   INTEGER NOT NULL DEFAULT 0,
    longitud           REAL    NOT NULL,
    latitud            REAL    NOT NULL,
    huso_gmt           INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id_aeropuerto),
    FOREIGN KEY (id_ciudad) REFERENCES ciudad (id_ciudad)
);

CREATE TABLE IF NOT EXISTS aerolinea (
    id_aerolinea  TEXT NOT NULL,
    nombre        TEXT NOT NULL,
    PRIMARY KEY (id_aerolinea)
);

CREATE TABLE IF NOT EXISTS vuelo_programado (
    id_vuelo_programado   TEXT    NOT NULL,
    codigo                TEXT    NOT NULL,
    hora_salida           TEXT    NOT NULL,
    hora_llegada          TEXT    NOT NULL,
    capacidad_maxima      INTEGER NOT NULL,
    id_aeropuerto_origen  TEXT    NOT NULL,
    id_aeropuerto_destino TEXT    NOT NULL,
    PRIMARY KEY (id_vuelo_programado),
    FOREIGN KEY (id_aeropuerto_origen)  REFERENCES aeropuerto (id_aeropuerto),
    FOREIGN KEY (id_aeropuerto_destino) REFERENCES aeropuerto (id_aeropuerto)
);

CREATE TABLE IF NOT EXISTS vuelo_instancia (
    id_vuelo_instancia    TEXT    NOT NULL,
    id_vuelo_programado   TEXT    NOT NULL,
    codigo                TEXT    NOT NULL,
    fecha_salida          TEXT    NOT NULL,
    fecha_llegada         TEXT    NOT NULL,
    capacidad_maxima      INTEGER NOT NULL,
    capacidad_disponible  INTEGER NOT NULL,
    id_aeropuerto_origen  TEXT    NOT NULL,
    id_aeropuerto_destino TEXT    NOT NULL,
    estado                TEXT    NOT NULL DEFAULT 'PROGRAMADO',
    PRIMARY KEY (id_vuelo_instancia),
    FOREIGN KEY (id_vuelo_programado)   REFERENCES vuelo_programado (id_vuelo_programado),
    FOREIGN KEY (id_aeropuerto_origen)  REFERENCES aeropuerto (id_aeropuerto),
    FOREIGN KEY (id_aeropuerto_destino) REFERENCES aeropuerto (id_aeropuerto)
);

CREATE TABLE IF NOT EXISTS pedido (
    id_pedido             TEXT    NOT NULL,
    id_aeropuerto_origen  TEXT    NOT NULL,
    id_aeropuerto_destino TEXT    NOT NULL,
    fecha_registro        TEXT    NOT NULL,
    fecha_hora_plazo      TEXT,
    cantidad_maletas      INTEGER NOT NULL,
    estado                TEXT    NOT NULL,
    PRIMARY KEY (id_pedido),
    FOREIGN KEY (id_aeropuerto_origen)  REFERENCES aeropuerto (id_aeropuerto),
    FOREIGN KEY (id_aeropuerto_destino) REFERENCES aeropuerto (id_aeropuerto)
);

CREATE TABLE IF NOT EXISTS maleta (
    id_maleta      TEXT NOT NULL,
    id_pedido      TEXT NOT NULL,
    fecha_registro TEXT NOT NULL,
    fecha_llegada  TEXT,
    estado         TEXT NOT NULL,
    PRIMARY KEY (id_maleta),
    FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido)
);

CREATE TABLE IF NOT EXISTS ruta (
    id_ruta           TEXT    NOT NULL,
    id_maleta         TEXT    NOT NULL,
    plazo_maximo_dias REAL    NOT NULL,
    duracion          REAL    NOT NULL DEFAULT 0,
    estado            TEXT    NOT NULL,
    PRIMARY KEY (id_ruta),
    FOREIGN KEY (id_maleta) REFERENCES maleta (id_maleta),
    UNIQUE (id_maleta)
);

CREATE TABLE IF NOT EXISTS ruta_vuelo_instancia (
    id_ruta            TEXT    NOT NULL,
    id_vuelo_instancia TEXT    NOT NULL,
    orden              INTEGER NOT NULL,
    PRIMARY KEY (id_ruta, id_vuelo_instancia),
    FOREIGN KEY (id_ruta)            REFERENCES ruta (id_ruta),
    FOREIGN KEY (id_vuelo_instancia) REFERENCES vuelo_instancia (id_vuelo_instancia)
);
