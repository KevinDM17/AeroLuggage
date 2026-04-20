# Class Model Specification (Domain + Optimization)

## Overview

This document defines the class structure of a logistics and route planning system for baggage transportation using flights. It includes domain entities, relationships, and optimization strategies.

---

## Domain Entities

### Ciudad

* idCiudad: String
* nombre: String
* continente: Continente

---

### Aeropuerto

* idAeropuerto: String
* ciudad: Ciudad
* capacidadAlmacen: int
* maletasActuales: int
* longitud: float
* latitud: float

---

### Aerolinea

* idAerolinea: String
* nombre: String

---

### Pedido

* idPedido: String
* aeropuertoOrigen: Aeropuerto
* aeropuertoDestino: Aeropuerto
* fechaRegistro: Date
* cantidadMaletas: int
* estado: String

#### Methods

* registrarPedido()

---

### Maleta

* idMaleta: String
* pedido: Pedido
* fechaRegistro: Date
* fechaLlegada: Date
* estado: String

---

### Ruta

* idRuta: String
* idMaleta: String
* plazoMaximoDias: double
* duracion: double
* subrutas: List<VueloInstancia>
* estado: String

#### Methods

* calcularPlazo()
* replanificar()

---

### VueloProgramado

* idVueloProgramado: String
* codigo: String
* horaSalida: DateTime
* horaLlegada: DateTime
* capacidadMaxima: int
* aeropuertoOrigen: Aeropuerto
* aeropuertoDestino: Aeropuerto

---

### VueloInstancia

* idVueloInstancia: String
* codigo: String
* fechaSalida: DateTime
* fechaLlegada: DateTime
* capacidadMaxima: int
* capacidadDisponible: int
* aeropuertoOrigen: Aeropuerto
* aeropuertoDestino: Aeropuerto
* estado: EstadoVuelo

#### Methods

* cancelar()
* actualizarCapacidad()

---

## Optimization Layer

### Metaheuristico (Abstract)

* Defines a generic optimization strategy

---

### ACO (Ant Colony Optimization)

**Extends:** Metaheuristico

#### Methods

* ejecutar(instancia: InstanciaProblema)
* evaluar()

---

### GA (Genetic Algorithm)

**Extends:** Metaheuristico

#### Methods

* ejecutar(instancia: InstanciaProblema)
* evaluar()

---

## Supporting Types

### Continente (Enum)

ASIA
EUROPA
AMERICA DEL SUR
AMERICA DEL NORTE 
CENTRO AMERICA
OCEANIA
AFRICA

---

### EstadoVuelo (Enum)
PROGRAMADO
CONFIRMADO
EN PROGRESO
FINALIZADO
CANCELADO

---

## Relationships

* Ciudad 1 ─── 1..* Aeropuerto
* Aeropuerto 1 ─── * Pedido (origen/destino)
* Pedido 1 ─── * Maleta
* Maleta 1 ─── 1 Ruta
* Ruta 1 ─── * VueloInstancia
* VueloInstancia * ─── 1 Aeropuerto (origen/destino)
* VueloProgramado 1 ─── * VueloInstancia

---

## Core Flow

Pedido → Maletas → Ruta → VueloInstancia

---

## Design Notes

* `VueloProgramado` represents a recurring flight definition.
* `VueloInstancia` represents a specific execution of that flight on a given date.
* `Ruta` is composed of multiple flight instances (multi-leg path).
* Optimization algorithms (ACO, GA) generate routes based on constraints.

---
