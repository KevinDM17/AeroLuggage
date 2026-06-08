import { useCallback, useEffect, useState } from "react";
import { getStompClient, isStompEnabled, whenConnected } from "../api/stomp";

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
    if (!enabled || !topic || !isStompEnabled()) {
      setData(null);
      setError(null);
      setConnected(false);
      return undefined;
    }

    let subscription = null;
    let cancelled = false;

    whenConnected()
      .then((client) => {
        if (cancelled) return;
        setConnected(true);
        subscription = client.subscribe(topic, (message) => {
          try {
            setData(JSON.parse(message.body));
            setError(null);
          } catch (e) {
            setError(e);
          }
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
      return;
    }
    const client = await whenConnected();
    client.publish({
      destination,
      body: JSON.stringify(body ?? {}),
    });
  }, []);
}
