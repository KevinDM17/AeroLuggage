# Especificación del archivo `planes_vuelo.txt` (para Java)

## 1. Descripción general

El archivo contiene vuelos en formato plano.
Cada línea representa un vuelo con:

* aeropuerto origen
* aeropuerto destino
* hora de salida
* hora de llegada
* capacidad de maletas

Formato:

```txt
SKBO-SEQM-03:34-04:21-0300
```

---

## 2. Estructura de cada línea

```txt
[ORIGEN]-[DESTINO]-[SALIDA]-[LLEGADA]-[DURACION]
```

Separador: `-`

---


## 3. Consideraciones importantes

### 3.1 Validación

Verificar:

```java
partes.length == 5
```

---

### 3.2 Formato estricto

* Siempre hay `-`
* No hay espacios
* Formato consistente

---

### 3.3 Problema del dataset

Duraciones como:

```txt
0360
```

3h 60m → 4h reales

---
