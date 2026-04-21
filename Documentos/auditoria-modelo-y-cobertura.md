# Auditoría de modelo y cobertura

## Dictamen

- `Diagrama`: cumplimiento parcial.
- `Exigencias`: cobertura parcial; no es suficiente para afirmar cumplimiento completo del sistema.

## Matriz de cumplimiento frente al diagrama

| Elemento | Estado | Observación |
| --- | --- | --- |
| `Ciudad` | Cumple | Atributos alineados con el diagrama. |
| `Aeropuerto` | Cumple | Atributos alineados con el diagrama. |
| `Aerolinea` | Parcial | Existe, pero el diagrama no detalla comportamiento adicional. |
| `Pedido` | Parcial | Se añadió `fechaHoraPlazo`, `registrarPedido()` y `calcularFechaHoraPlazo()`. |
| `Maleta` | Cumple | Atributos alineados con el diagrama. |
| `Ruta` | Parcial | Se añadieron `calcularPlazo()` y `replanificar()` con comportamiento mínimo. |
| `VueloProgramado` | Cumple | Atributos visibles en el diagrama presentes en el modelo. |
| `VueloInstancia` | Parcial | Se añadieron `cancelar()` y `actualizarCapacidad()`. |
| `InstanciaProblema` | Parcial | Se añadieron búsquedas por id y `toString()`. |
| `Solucion` | Parcial | Se normalizó como contenedor de `ArrayList<Ruta>` y se mantuvo compatibilidad. |
| `Metaheuristico` | Cumple | Jerarquía abstracta presente. |
| `ACO` | Parcial | Existe, pero `ejecutar(...)` y `evaluar()` siguen vacíos. |
| `GA` | Parcial | Existe, pero `ejecutar(...)` y `evaluar()` siguen vacíos. |
| `Continente` | Cumple | Enumeración presente. |
| `EstadoVuelo` | Cumple | Enumeración presente. |

## Cobertura parcial frente a `Documentos/situacion.txt`

| Frente funcional | Estado | Observación |
| --- | --- | --- |
| Planificación de rutas | Parcial | Existe base de dominio y jerarquía metaheurística, pero no hay lógica operativa completa. |
| Replanteamiento ante contingencias | Parcial | `Ruta` y `VueloInstancia` tienen métodos mínimos, pero no existe flujo de replanificación integral. |
| Monitoreo en tiempo real | No cumple | No hay controladores, servicios, eventos ni actualización en vivo. |
| Reportes | No cumple | No hay capa de reportes ni agregación de indicadores. |
| Simulación por periodos | No cumple | No hay casos de uso ni orquestación temporal. |
| Análisis de colapso operativo | No cumple | No existe lógica dedicada para estrés o colapso. |
| Persistencia | No cumple | No hay repositorios ni entidades persistentes. |
| Integración de mapa | No cumple | No hay frontend ni integración geográfica. |

## Observaciones

- El XML del diagrama parece contener typos como `Rutas`, `Vuelo` y `aueropuerto`; no se replicaron en código.
- La evaluación de exigencias se mantuvo parcial porque el PDF original no pudo extraerse desde este entorno.
