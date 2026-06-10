import { useEffect } from "react";
import { clearPerformanceTimeline } from "../utils/performanceCleanup";

const CLEANUP_INTERVAL_MS = 60_000;

export function useDefensivePerformanceCleanup(isSimulationRunning: boolean): void {
  useEffect(() => {
    if (!isSimulationRunning) return;

    clearPerformanceTimeline();

    const intervalId = window.setInterval(() => {
      clearPerformanceTimeline();
    }, CLEANUP_INTERVAL_MS);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [isSimulationRunning]);
}
