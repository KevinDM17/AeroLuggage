## Objetivo general
Construye las clases base del sistema a partir del diagrama de clases adjunto. Debes crear una clase por cada entidad identificada, respetando exactamente los nombres de las entidades y sus atributos. En esta primera versión, prioriza la definición de atributos, constructores, getters, setters y una estructura limpia del dominio. Los métodos del diagrama pueden declararse como esqueletos vacíos si todavía no se implementará la lógica.

## Reglas generales de construcción
1. Crea una clase Java independiente por cada entidad del diagrama.
2. Usa atributos privados.
3. Genera constructor vacío y constructor completo.
4. Genera métodos `get` y `set` para todos los atributos.
5. Genera `toString()` para facilitar pruebas.
6. Mantén los nombres de atributos tal como aparecen en el diagrama.
7. Usa `String`, `int`, `double`, `float` y `Date` según corresponda.
8. Si una clase es abstracta en el diagrama, declárala como `abstract`.
9. Los métodos del diagrama pueden dejarse definidos sin lógica al inicio.
10. No agregues atributos extra salvo que se requiera técnicamente y se documente.

---

## Entidades y atributos

### 1. Aerolinea
**Clase:** `Aerolinea`

**Atributos:**
- `String idAerolinea`
- `String nombre`

---

### 2. Envio
**Clase:** `Envio`

**Atributos:**
- `String idEnvio`
- `String idAeropuertoSalida`
- `String idAeropuertoLlegada`
- `Date fechaRegistro`
- `int cantidadMaletas`
- `String estado`

**Método base del diagrama:**
- `registrarEnvio()`

---

### 5. Maleta
**Clase:** `Maleta`

**Atributos:**
- `String idMaleta`
- `String idEnvio`
- `String estado`

---


### 7. Planificador
**Clase:** `Planificador`

**Atributos:**
- `String idPlanificador`

**Métodos base del diagrama:**
- `planificarRutas()`
- `replanificarRutas()`
- `evaluarEscenario()`

---

### 8. Ruta
**Clase:** `Ruta`

**Atributos:**
- `String idRuta`
- `double plazoMaximoDias`
- `String estado`

**Métodos base del diagrama:**
- `calcularPlazo()`
- `replanificar()`

---




### 9. MetaheuristicaA
**Clase:** `MetaheuristicaA`


**Método base del diagrama:**
- `ejecutar()`
- `evaluar()`

---

### 10. MetaheuristicaB
**Clase:** `MetaheuristicaB`



**Método base del diagrama:**
- `ejecutar()`
- `evaluar()`


---


### 12. Vuelo
**Clase:** `Vuelo`

**Atributos:**
- `String idVuelo`
- `String codigo`
- `Date fechaSalida`
- `Date fechaLlegada`
- `int capacidadMaxima`
- `int capacidadDisponible`
- `String estado`

**Métodos base del diagrama:**
- `cancelar()`
- `actualizarCapacidad()`

---

### 13. Ciudad
**Clase:** `Ciudad`

**Atributos:**
- `String idCiudad`
- `String nombre`
- `Continente continente`

---
### 13. Continente
**Clase:** `Continente`

**Atributos:**
- `ASIA`
- `EUROPA`
- `AMERICA DEL SUR`
- `AMERICA DEL NORTE`
- `CENTROAMERICA`
- `OCEANIA`
- `AFRICA`

---
### 14. Aeropuerto
**Clase:** `Aeropuerto`

**Atributos:**
- `String idAeropuerto`
-`String idCiudad`
- `String nombre`
- `int capacidadAlmacen`
- `int maletasActuales`
- `float longitud`
- `float latitud`



## Orden sugerido de implementación
Implementa primero estas clases base, porque son el núcleo del dominio:
1. `Aerolinea`
2. `Envio`
3. `Maleta`
4. `Ruta`
6. `Vuelo`
7. `Aeropuerto`
8. `Ciudad`

Después implementa las clases de soporte:
12. `Planificador`

Finalmente implementa la jerarquía de algoritmos:
13. `MetaheuristicaA`
14. `MetaheuristicaB`

---

## Indicación final para el agente
No implementes todavía la lógica de negocio compleja ni las relaciones avanzadas entre clases, salvo que se solicite después. En esta etapa, entrega una base sólida del modelo de dominio con clases bien definidas, tipadas y listas para evolucionar en fases posteriores.