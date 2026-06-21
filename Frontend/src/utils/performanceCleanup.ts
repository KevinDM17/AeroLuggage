export function clearPerformanceTimeline(): void {
  try {
    if (typeof performance === "undefined") return;

    performance.clearMeasures?.();
    performance.clearMarks?.();

    if (typeof performance.clearResourceTimings === "function") {
      performance.clearResourceTimings();
    }
  } finally {
  }
}
