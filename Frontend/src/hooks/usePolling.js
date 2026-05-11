import { useEffect, useRef, useState } from "react";

const DEFAULT_INTERVAL = Number(import.meta.env.VITE_POLL_INTERVAL_MS) || 5000;

/**
 * Llama a `fn` cada `intervalMs` mientras `enabled` sea true.
 * Pensado para KPIs y estado de simulaciones.
 *
 *   const { data, error } = usePolling(getStatus, { enabled: true });
 */
export function usePolling(fn, { enabled = true, intervalMs = DEFAULT_INTERVAL } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;

    const run = async () => {
      try {
        const result = await fnRef.current();
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) setError(e);
      }
    };

    run();
    const id = setInterval(run, intervalMs);
    return () => { cancelled = true; clearInterval(id); };
  }, [enabled, intervalMs]);

  return { data, error };
}
