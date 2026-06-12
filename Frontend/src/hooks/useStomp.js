import { useCallback, useEffect, useState } from "react";
import { isStompEnabled, whenConnected, subscribeToReconnects } from "../api/stomp";

export function useStompSubscribe(topic, { enabled = true } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [connected, setConnected] = useState(false);
  const [reconnectToken, setReconnectToken] = useState(0);

  useEffect(() => {
    return subscribeToReconnects(() => setReconnectToken((c) => c + 1));
  }, []);

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
