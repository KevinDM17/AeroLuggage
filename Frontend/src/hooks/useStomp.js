import { useCallback, useEffect, useState } from "react";
import { getStompClient, isStompEnabled, whenConnected } from "../api/stomp";
import { parseStompBody } from "../api/stompParser";

/**
 * Se suscribe a un topic STOMP y devuelve el ultimo mensaje recibido (parseado a JSON).
 *
 *   const { data, error, connected } = useStompSubscribe("/topic/simulacion/abc-123");
 *
 * Si `topic` es null/undefined o `enabled=false`, no se suscribe.
 * Si VITE_USE_MOCK=true, no hace nada (devuelve data=null).
 */
export function useStompSubscribe(topic, { enabled = true } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!enabled || !topic || !isStompEnabled()) return undefined;

    let subscription = null;
    let cancelled = false;

    whenConnected()
      .then((client) => {
        if (cancelled) return;
        setConnected(true);
        subscription = client.subscribe(topic, (message) => {
          /* Parseamos via worker para payloads pesados (snapshots de
           * simulacion). El main thread no se bloquea durante el JSON.parse
           * de cientos de KB, asi la animacion del mapa sigue a 60-120 fps
           * sin pausas. parseStompBody resuelve inmediato (sync resolve)
           * para payloads chicos. */
          parseStompBody(message.body).then(
            (parsed) => {
              if (cancelled) return;
              setData(parsed);
              setError(null);
            },
            (e) => {
              if (cancelled) return;
              setError(e);
            },
          );
        });
      })
      .catch((e) => {
        if (!cancelled) setError(e);
      });

    return () => {
      cancelled = true;
      if (subscription) subscription.unsubscribe();
    };
  }, [topic, enabled]);

  return { data, error, connected };
}

/**
 * Devuelve una funcion para publicar mensajes a destinos /app/...
 *
 *   const publish = useStompPublish();
 *   publish("/app/simulacion/periodo/detener", { sessionId });
 */
export function useStompPublish() {
  return useCallback(async (destination, body) => {
    if (!isStompEnabled()) {
      console.warn("[STOMP] publish ignorado en modo mock:", destination);
      return;
    }
    const client = await whenConnected();
    client.publish({
      destination,
      body: JSON.stringify(body ?? {}),
    });
  }, []);
}
