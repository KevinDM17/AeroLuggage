# Especificación del archivo `_envios_*.txt` (para Java)

## 1. Descripción general

El archivo contiene pedidos de envío de maletas.
Cada archivo está asociado a **un aeropuerto origen**, el cual se identifica en el **nombre del archivo**.

Cada línea representa un pedido individual.

Formato conceptual:

```txt
ID-YYYYMMDD-HH-MM-DESTINO-CANTIDAD-CLIENTE
```

Ejemplo:

```txt
000000001-20260102-00-47-SUAA-002-0032535
```

---

## 2. Estructura de cada línea

```txt
[ID]-[FECHA]-[HORA]-[MINUTO]-[DESTINO]-[CANTIDAD]-[CLIENTE]
```

Separador: `-`

---

## 3. Campos

* **ID** → identificador del pedido (9 dígitos con ceros)
* **FECHA** → fecha de registro (formato `YYYYMMDD`)
* **HORA** → hora de registro (`HH`)
* **MINUTO** → minuto de registro (`MM`)
* **DESTINO** → código ICAO del aeropuerto destino
* **CANTIDAD** → número de maletas (3 dígitos con ceros)
* **CLIENTE** → identificador del cliente (7 dígitos)

---

## 4. Consideraciones importantes

### 4.1 Validación de estructura

Verificar:

```java
partes.length == 7
```

---

### 4.2 Formato estricto

* Siempre hay `-` como separador
* No hay espacios entre campos
* Orden fijo de campos

---

### 4.3 Nombre del archivo

El nombre del archivo sigue el patrón:

```txt
_envios_XXXX_.txt
```

Donde `XXXX` es el código ICAO del aeropuerto origen.

Ejemplos:

```txt
_envios_SKBO_.txt
_envios_EBCI_.txt
_envios_SEQM_.txt
```

El código ICAO se extrae del nombre del archivo.

---

### 4.4 Formato de fecha

```txt
20260404
```

Interpretación:

* Año: 2026
* Mes: 04
* Día: 04

Recomendado en Java:

```java
LocalDate.parse(fecha, DateTimeFormatter.ofPattern("yyyyMMdd"));
```

---

### 4.5 Formato de hora y minuto

La hora y minuto están en campos separados (no usan dos puntos).

```txt
00-47
```

Significa: hora 00, minuto 47.

Recomendado en Java:

```java
int hora = Integer.parseInt(partes[2].trim());
int minuto = Integer.parseInt(partes[3].trim());
LocalTime.of(hora, minuto);
```

---

### 4.6 Cantidad de maletas

* Debe ser un número entero positivo
* Puede requerir validación:

```java
cantidad > 0
```

---

### 4.7 Identificadores

* ID → 9 dígitos con ceros a la izquierda
* Cliente → 7 dígitos

---

