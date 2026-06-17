import { createContext, useContext } from "react";

/**
 * Estado compartido panel <-> mapa.
 *
 * - mapHighlight: ruta resaltada a demanda (maleta/envio) -> req 1-4.
 *     { kind, label, rutas:[{idMaleta, escalas:[{codigo,origen,destino,salida,llegada}]}], legs:[{origen,destino,codigo}] }
 * - selected: entidad seleccionada, resaltada en mapa Y panel -> req 5-8.
 *     { kind: "airport" | "flight", id }
 * - mapFocus: peticion de enfoque de camara (panel -> mapa) -> req 5,7,9.
 *     { lng, lat, zoom, ts }
 * - panelFocus: peticion de enfoque en el panel (mapa -> panel) -> req 6,8.
 *     { kind, id, ts }
 * - mapDim: reflejo de filtros del panel en el mapa -> req 10-13.
 *     { airports: Set<code> | null, flights: Set<id> | null }   (null = sin filtro)
 */
export const MapFocusContext = createContext({
  mapHighlight: null,
  setMapHighlight: () => {},
  selected: null,
  setSelected: () => {},
  mapFocus: null,
  setMapFocus: () => {},
  panelFocus: null,
  setPanelFocus: () => {},
  mapDim: { airports: null, flights: null },
  setMapDim: () => {},
  // Loader de manifiesto de vuelo (pedidos + maletas) que el RightPanel publica
  // para que el mapa pueda mostrar la info al hacer click en un avion.
  flightManifestLoader: null,
  setFlightManifestLoader: () => {},
});

export const useMapFocus = () => useContext(MapFocusContext);
