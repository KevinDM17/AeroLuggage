# AeroLuggage

AeroLuggage es un sistema de optimización para la gestión de rutas de equipaje aéreo. El proyecto busca asignar rutas eficientes a maletas considerando vuelos disponibles, capacidad de aeropuertos, capacidad de vuelos, tiempos límite de entrega y restricciones operativas dentro de una simulación por periodos.

## Descripción del problema

En una operación aérea, las maletas no siempre pueden ser transportadas directamente desde el aeropuerto de origen hasta el aeropuerto de destino. La disponibilidad de vuelos, la capacidad de carga, los horarios y las restricciones de almacenamiento pueden generar congestión, retrasos o maletas sin ruta asignada.

AeroLuggage propone una solución basada en algoritmos de optimización para construir rutas viables para cada maleta, respetando las restricciones del sistema y priorizando la entrega dentro del plazo máximo permitido.

## Objetivo del proyecto

Diseñar e implementar un sistema capaz de asignar rutas a maletas mediante algoritmos de optimización, comparando el rendimiento de un algoritmo genético híbrido y un algoritmo basado en colonia de hormigas.

## Características principales

- Registro y procesamiento de maletas asociadas a envíos.
- Modelado de aeropuertos, vuelos programados y vuelos instancia.
- Asignación de rutas con una o varias escalas.
- Control de capacidad por vuelo y aeropuerto.
- Simulación por ventanas de tiempo dentro de un periodo determinado.
- Comparación experimental entre algoritmos de optimización.
- Evaluación mediante una función objetivo basada en penalizaciones y costos operativos.

## Modelo general del sistema

El sistema trabaja con las siguientes entidades principales:

- **Aeropuerto:** representa un punto de salida, llegada o escala. Tiene restricciones de capacidad.
- **Vuelo programado:** representa un vuelo recurrente definido por origen, destino y horario.
- **Vuelo instancia:** representa la ejecución concreta de un vuelo en una fecha determinada.
- **Envío:** agrupa información general de una solicitud de transporte.
- **Maleta:** unidad que debe ser ruteada desde un origen hasta un destino.
- **Ruta:** solución asignada a una maleta mediante una secuencia de vuelos.
- **TramoRuta:** representa cada segmento de una ruta.

## Algoritmos utilizados

### Algoritmo Genético Híbrido

El algoritmo genético híbrido utiliza una heurística inicial basada en inserción tipo Solomon para generar soluciones iniciales. Luego, aplica operadores evolutivos como selección, cruce, mutación y reparación de soluciones.

El objetivo es mejorar progresivamente la asignación de rutas, reduciendo la cantidad de maletas no ruteadas y optimizando el uso de capacidades.

### Algoritmo de Colonia de Hormigas

El algoritmo de colonia de hormigas construye soluciones mediante agentes que seleccionan vuelos según feromonas y criterios heurísticos. Las feromonas se actualizan según la calidad de las rutas encontradas.

Este enfoque permite explorar diferentes combinaciones de vuelos y reforzar aquellas decisiones que generan mejores soluciones.

## Función objetivo
