import { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Polyline } from "react-leaflet";
import L from "leaflet";
import { tokens, semaphoreColor } from "../../utils/tokens";

const AIRPORTS = [
  { id: 'JFK', lat: 40.6413, lng: -73.7781, status: 'green' },
  { id: 'MIA', lat: 25.7959, lng: -80.2870, status: 'red' },
  { id: 'BOG', lat: 4.7016, lng: -74.1469, status: 'green' },
  { id: 'LIM', lat: -12.0219, lng: -77.1143, status: 'yellow' },
  { id: 'GRU', lat: -23.4356, lng: -46.4731, status: 'yellow' },
  { id: 'MAD', lat: 40.4719, lng: -3.5626, status: 'green' },
  { id: 'LHR', lat: 51.4700, lng: -0.4543, status: 'green' },
  { id: 'DXB', lat: 25.2532, lng: 55.3657, status: 'yellow' },
];

const ROUTES = [
  { id: 1, origin: 'JFK', dest: 'MIA', used: 60, capacity: 250 },
  { id: 2, origin: 'MIA', dest: 'MAD', used: 300, capacity: 350 },
  { id: 3, origin: 'MIA', dest: 'GRU', used: 150, capacity: 200 },
  { id: 4, origin: 'LIM', dest: 'BOG', used: 45, capacity: 220 },
  { id: 5, origin: 'LHR', dest: 'DXB', used: 380, capacity: 400 },
];

const getStatusColor = semaphoreColor;

const getFlightLoadColor = (used, capacity) => {
  const pct = capacity > 0 ? (used / capacity) * 100 : 0;
  if (pct >= 85) return tokens.danger;
  if (pct >= 60) return tokens.warning;
  return tokens.success;
};

const createAirportIcon = (airport) => {
  const color = getStatusColor(airport.status);
  return L.divIcon({
    html: `
      <div style="display: flex; flex-direction: column; items-center; justify-content: center; transform: translate(-50%, -50%);">
        <div style="width: 12px; height: 12px; background-color: ${color}; border-radius: 50%; box-shadow: 0 0 10px ${color}, 0 0 20px ${color}; margin: 0 auto;"></div>
        <div style="color: white; font-size: 10px; font-weight: bold; font-family: sans-serif; background: rgba(0,0,0,0.5); padding: 2px 4px; border-radius: 4px; margin-top: 4px; white-space: nowrap;">
          ${airport.id}
        </div>
      </div>
    `,
    className: '',
    iconSize: [0, 0], // Handled in html transform
    iconAnchor: [0, 0],
  });
};

const createPlaneIcon = (angle, color) => {
  return L.divIcon({
    html: `
      <div style="width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; transform: rotate(${angle}deg);">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="${color}" stroke="${color}" stroke-width="1" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.2-1.1.5l-1.3 1.5c-.3.4-.1 1 .4 1.2L9 12l-4 4-2.2-.6c-.4-.1-.8.1-1.1.4l-.8.8c-.3.4-.1 1 .4 1.2l4 1.5 1.5 4c.2.5.8.7 1.2.4l.8-.8c.3-.3.5-.7.4-1.1L8 19l4-4 2.6 6.2c.2.5.8.7 1.2.4l1.5-1.3c.3-.2.6-.6.5-1.1z"/>
        </svg>
      </div>
    `,
    className: '',
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });
};


export default function AirportMap({ showFlights = true }) {
  const center = [20, -40];
  const zoom = 3;

  // Animation state for planes
  const [progresses, setProgresses] = useState(ROUTES.map(() => Math.random()));

  useEffect(() => {
    let animationFrame;
    let lastTime = performance.now();

    const animate = (time) => {
      const deltaTime = time - lastTime;
      lastTime = time;

      setProgresses(prev => prev.map(p => {
        // Different speeds for different planes for variation
        const newProgress = p + (deltaTime * 0.00005);
        return newProgress % 1; // Wrap around
      }));
      
      animationFrame = requestAnimationFrame(animate);
    };

    animationFrame = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(animationFrame);
  }, []);

  return (
    <div className="w-full h-full bg-canvas">
       <MapContainer center={center} zoom={zoom} style={{ height: "100%", width: "100%", background: "transparent" }} zoomControl={false} attributionControl={false}>
          <TileLayer
             url="https://{s}.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}{r}.png"
          />
          
          {showFlights && ROUTES.map((route, i) => {
             const origin = AIRPORTS.find(a => a.id === route.origin);
             const destination = AIRPORTS.find(a => a.id === route.dest);
             if (!origin || !destination) return null;

             const progress = progresses[i];
             
             // Calculate interpolated position
             const lat = origin.lat + (destination.lat - origin.lat) * progress;
             const lng = origin.lng + (destination.lng - origin.lng) * progress;
             
             // Bearing aprox (norte=0, este=90, sur=180, oeste=270) usando lat/lng como cartesiano.
             // Suficiente para visualización a zoom global; no es navegación real.
             const bearing = Math.atan2(destination.lng - origin.lng, destination.lat - origin.lat) * 180 / Math.PI;
             // El icono Plane de lucide apunta nativamente al NE (~45° CW desde el norte),
             // así que restamos 45° para alinear la punta con el bearing.
             const planeAngle = bearing - 45;

             return (
               <div key={route.id}>
                 <Polyline 
                    positions={[[origin.lat, origin.lng], [destination.lat, destination.lng]]}
                    color={tokens.success}
                    weight={1.5}
                    opacity={0.3}
                    dashArray="4, 6"
                 />
                 <Marker 
                    position={[lat, lng]} 
                    icon={createPlaneIcon(planeAngle, getFlightLoadColor(route.used, route.capacity))} 
                 />
               </div>
             );
          })}

          {AIRPORTS.map((airport) => (
             <Marker 
                key={airport.id} 
                position={[airport.lat, airport.lng]}
                icon={createAirportIcon(airport)}
             />
          ))}
       </MapContainer>
    </div>
  );
}
