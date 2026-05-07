// Mock data for Airports, Routes, etc.

export const AIRPORTS = [
  { id: 'LIM', name: 'Jorge Chávez Intl', city: 'Lima', country: 'Peru', lat: -12.0219, lng: -77.1143, capacity: 5000, currentLoad: 2100, status: 'normal' },
  { id: 'BOG', name: 'El Dorado Intl', city: 'Bogotá', country: 'Colombia', lat: 4.7016, lng: -74.1469, capacity: 8000, currentLoad: 4500, status: 'warning' },
  { id: 'SCL', name: 'Arturo Merino Benítez', city: 'Santiago', country: 'Chile', lat: -33.3930, lng: -70.7858, capacity: 6000, currentLoad: 1200, status: 'normal' },
  { id: 'EZE', name: 'Ministro Pistarini Intl', city: 'Buenos Aires', country: 'Argentina', lat: -34.8222, lng: -58.5358, capacity: 6500, currentLoad: 5000, status: 'warning' },
  { id: 'MAD', name: 'Adolfo Suárez Madrid-Barajas', city: 'Madrid', country: 'Spain', lat: 40.4719, lng: -3.5626, capacity: 15000, currentLoad: 8000, status: 'normal' },
  { id: 'MIA', name: 'Miami Intl', city: 'Miami', country: 'USA', lat: 25.7959, lng: -80.2870, capacity: 12000, currentLoad: 11000, status: 'critical' },
  { id: 'MEX', name: 'Benito Juárez Intl', city: 'Mexico City', country: 'Mexico', lat: 19.4361, lng: -99.0719, capacity: 9000, currentLoad: 4000, status: 'normal' },
];

export const MOCK_ROUTES = [
  { id: 'R1', origin: 'LIM', destination: 'MIA', flightId: 'AA918', departureTime: 480, arrivalTime: 840, capacity: 300, currentLoad: 250, status: 'active' },
  { id: 'R2', origin: 'BOG', destination: 'MAD', flightId: 'AV10', departureTime: 960, arrivalTime: 1560, capacity: 400, currentLoad: 390, status: 'delayed' },
  { id: 'R3', origin: 'SCL', destination: 'LIM', flightId: 'LA530', departureTime: 600, arrivalTime: 810, capacity: 250, currentLoad: 120, status: 'completed' },
  { id: 'R4', origin: 'EZE', destination: 'MIA', flightId: 'AR1302', departureTime: 1200, arrivalTime: 1800, capacity: 350, currentLoad: 350, status: 'active' },
  { id: 'R5', origin: 'MEX', destination: 'BOG', flightId: 'AM761', departureTime: 540, arrivalTime: 870, capacity: 200, currentLoad: 180, status: 'active' },
  { id: 'R6', origin: 'MAD', destination: 'LIM', flightId: 'UX75', departureTime: 1440, arrivalTime: 2160, capacity: 450, currentLoad: 0, status: 'scheduled' },
];

export const MOCK_SUITCASES = [
  { id: 'BAG-1001', origin: 'LIM', destination: 'MAD', assignedRoute: 'LIM -> BOG -> MAD', status: 'routed', weight: 23, priority: 'standard' },
  { id: 'BAG-1002', origin: 'SCL', destination: 'MIA', assignedRoute: 'SCL -> LIM -> MIA', status: 'routed', weight: 15, priority: 'high' },
  { id: 'BAG-1003', origin: 'EZE', destination: 'MEX', assignedRoute: 'Unassigned', status: 'pending', weight: 20, priority: 'standard' },
  { id: 'BAG-1004', origin: 'BOG', destination: 'MIA', assignedRoute: 'BOG -> MIA', status: 'delayed', weight: 28, priority: 'low' },
  { id: 'BAG-1005', origin: 'MEX', destination: 'MAD', assignedRoute: 'MEX -> BOG -> MAD', status: 'routed', weight: 21, priority: 'high' },
  { id: 'BAG-1006', origin: 'MIA', destination: 'EZE', assignedRoute: 'Unassigned', status: 'unrouted', weight: 25, priority: 'standard' },
];

export const ALGORITHM_COMPARATORS = [
  { time: '00:00', acoFitness: 100, hgFitness: 90, acoRouted: 50, hgRouted: 40 },
  { time: '04:00', acoFitness: 250, hgFitness: 300, acoRouted: 150, hgRouted: 200 },
  { time: '08:00', acoFitness: 600, hgFitness: 750, acoRouted: 400, hgRouted: 500 },
  { time: '12:00', acoFitness: 1200, hgFitness: 1400, acoRouted: 800, hgRouted: 950 },
  { time: '16:00', acoFitness: 2100, hgFitness: 2500, acoRouted: 1500, hgRouted: 1700 },
  { time: '20:00', acoFitness: 3400, hgFitness: 4000, acoRouted: 2500, hgRouted: 2800 },
  { time: '24:00', acoFitness: 4800, hgFitness: 5500, acoRouted: 4000, hgRouted: 4200 },
];

export const SIMULATION_LOGS = [
  { id: 1, timestamp: '08:00:01', type: 'info', message: 'Simulation started.' },
  { id: 2, timestamp: '08:00:05', type: 'success', message: 'ACO Algorithm initialized.' },
  { id: 3, timestamp: '08:01:23', type: 'warning', message: 'High load detected at MIA airport.' },
  { id: 4, timestamp: '08:05:40', type: 'error', message: 'Flight AV10 delayed.' },
  { id: 5, timestamp: '08:10:12', type: 'info', message: 'Re-routing suitcases from AV10...' },
];
