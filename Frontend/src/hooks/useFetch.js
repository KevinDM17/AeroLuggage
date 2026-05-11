import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Hook para llamar a una funcion async (idealmente del modulo src/api).
 * Devuelve { data, loading, error, refetch }.
 *
 *   const { data, loading, error, refetch } = useFetch(listFlights);
 *
 * Reejecuta automaticamente cuando cambian las deps.
 */
export function useFetch(fn, deps = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fn();
      if (mounted.current) setData(result);
    } catch (e) {
      if (mounted.current) setError(e);
    } finally {
      if (mounted.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => { run(); }, [run]);

  return { data, loading, error, refetch: run };
}
