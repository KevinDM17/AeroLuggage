import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig, loadEnv} from 'vite';

export default defineConfig(({mode}) => {
  const env = loadEnv(mode, '.', '');
  return {
    plugins: [react(), tailwindcss()],
    envPrefix: ['VITE_', 'BACKEND_'],
    define: {
      'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY),
      // sockjs-client (usado por @stomp/stompjs) referencia `global` que no
      // existe en el browser. globalThis funciona en browser y Node.
      global: 'globalThis',
    },
    optimizeDeps: {
      esbuildOptions: {
        define: {
          global: 'globalThis',
        },
      },
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      port: parseInt(env.PORT ?? '5173'),
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modifyâfile watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
    },
    build: {
      // Separar libs grandes (MapLibre + deck.gl) en chunks dedicados.
      // Beneficio: el navegador cachea esos chunks aparte; cuando cambias
      // codigo de la app, no re-descarga 1MB+ de libs. Ademas paraleliza
      // mejor la descarga inicial.
      rollupOptions: {
        output: {
          manualChunks: {
            maplibre: ['maplibre-gl', 'react-map-gl/maplibre'],
            deckgl: [
              '@deck.gl/core',
              '@deck.gl/react',
              '@deck.gl/layers',
              '@deck.gl/mapbox',
            ],
          },
        },
      },
      chunkSizeWarningLimit: 1200,
    },
  };
});
