# Especificación del archivo `aeropuertos.txt` (para Java)

## 1. Descripción general

El archivo contiene información de aeropuertos organizada por regiones geográficas.
Cada línea útil representa un aeropuerto con múltiples campos alineados por espacios.

El archivo incluye:

* Encabezado (metadata)
* Secciones (regiones)
* Registros de aeropuertos

Ejemplo:

```txt
01   SKBO   Bogota              Colombia        bogo    -5     430     Latitude: 04° 42' 05" N   Longitude:  74° 08' 49" W
```

---

## 2. Estructura de cada línea de aeropuerto

Formato lógico:

```txt
[INDICE] [ICAO] [CIUDAD] [PAIS] [CODIGO] [GMT] [CAPACIDAD] Latitude: ... Longitude: ...
```

Campos:

* índice → número correlativo
* ICAO → código del aeropuerto
* ciudad → puede tener múltiples palabras
* país → nombre del país
* código → identificador corto
* GMT → huso horario
* capacidad → capacidad operativa
* coordenadas → latitud y longitud en formato textual

---

## 3. Consideraciones importantes

### 3.1 No usar split simple por espacios

Incorrecto:

```java
line.split(" ");
```

Problema:

* Espacios variables
* Columnas alineadas manualmente
* Ciudades con múltiples palabras

Recomendado:

* Usar posiciones fijas, o
* Usar expresiones regulares

---

### 3.2 Líneas que NO son datos

El archivo contiene líneas que deben ignorarse:

* Encabezado:

```txt
PDDS 26-1 (basado en 2026.1)  20260404
```

* Separadores:

```txt
**************
```

* Regiones:

```txt
America del Sur.
Europa
Asia
```

Validar que la línea comience con un número:

```java
line.matches("^\\d+.*")
```

---

### 3.3 Ciudades con múltiples palabras

Ejemplo:

```txt
Santiago de Chile
Buenos Aires
```

No asumir que la ciudad es una sola palabra

---

### 3.4 Coordenadas

Formato:

```txt
Latitude: 04° 42' 05" N   Longitude:  74° 08' 49" W
```

Problemas posibles:

* Uso inconsistente de comillas:

```txt
00' en lugar de 00"
```

* Espacios variables
* Caracteres invisibles

---

### 3.5 Caracteres especiales

Ejemplo:

```txt
E﻿
```

Puede contener caracteres Unicode invisibles

Recomendado:

```java
line = line.replace("\uFEFF", "").trim();
```

---

### 3.6 Espaciado irregular

* No hay delimitador fijo
* Columnas alineadas manualmente
* Cantidad de espacios variable

---

### 3.7 Validación básica

Verificar que la línea tenga estructura válida:

```java
line.contains("Latitude") && line.contains("Longitude")
```

---

## 4. Recomendación final

Para parsing robusto en Java:

* Filtrar solo líneas válidas (que empiezan con número)
* Limpiar caracteres especiales
* Evitar `split(" ")`
* Usar regex o parsing por posiciones
* Manejar errores con try/catch

---
