import { useCallback, useEffect, useRef, useState } from "react";
import { isStompEnabled, whenConnected, subscribeToReconnects } from "../api/stomp";

export function useStompSubscribe(topic, { enabled = true } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [connected, setConnected] = useState(false);
  const [reconnectToken, setReconnectToken] = useState(0);
  const activeSub = useRef(null);

  useEffect(() => {
    return subscribeToReconnects(() => setReconnectToken((c) => c + 1));
  }, []);

  useEffect(() => {
    if (!enabled || !topic || !isStompEnabled()) {
      setData(null);
      setError(null);
      setConnected(false);
      activeSub.current?.unsubscribe();
      activeSub.current = null;
      return undefined;
    }

    let cancelled = false;

    whenConnected()
      .then((client) => {
        if (cancelled) return;
        activeSub.current?.unsubscribe();
        setConnected(true);
        const sub = client.subscribe(topic, (message) => {
          try {
            const parsed = JSON.parse(message.body);
            setData(parsed);
            setError(null);
          } catch (e) {
            setError(e);
            console.error("[STOMP] Error parseando mensaje en " + topic, e);
          }
        });
        activeSub.current = sub;
      })
      .catch((e) => {
        if (!cancelled) setError(e);
      });

    return () => {
      cancelled = true;
      activeSub.current?.unsubscribe();
      activeSub.current = null;
    };
  }, [topic, enabled, reconnectToken]);

  return { data, error, connected };
}

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
