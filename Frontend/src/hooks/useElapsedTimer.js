import { useEffect, useRef, useState } from "react";

export function useElapsedTimer(status, resetKey, intervalMs = 1000) {
  const [elapsedMs, setElapsedMs] = useState(0);
  const accumulatedMsRef = useRef(0);
  const runningSinceRef = useRef(null);
  const lastResetKeyRef = useRef(resetKey);

  useEffect(() => {
    if (lastResetKeyRef.current !== resetKey) {
      accumulatedMsRef.current = 0;
      runningSinceRef.current = null;
      lastResetKeyRef.current = resetKey;
      setElapsedMs(0);
    }

    if (status === "idle") {
      accumulatedMsRef.current = 0;
      runningSinceRef.current = null;
      setElapsedMs(0);
      return undefined;
    }

    if (status !== "running") {
      if (runningSinceRef.current !== null) {
        accumulatedMsRef.current += Date.now() - runningSinceRef.current;
        runningSinceRef.current = null;
        setElapsedMs(accumulatedMsRef.current);
      }
      return undefined;
    }

    runningSinceRef.current = Date.now();
    setElapsedMs(accumulatedMsRef.current);

    const intervalId = window.setInterval(() => {
      setElapsedMs(accumulatedMsRef.current + Date.now() - runningSinceRef.current);
    }, intervalMs);

    return () => {
      window.clearInterval(intervalId);
      if (runningSinceRef.current !== null) {
        accumulatedMsRef.current += Date.now() - runningSinceRef.current;
        runningSinceRef.current = null;
        setElapsedMs(accumulatedMsRef.current);
      }
    };
  }, [status, resetKey, intervalMs]);

  return elapsedMs;
}
