export function clearPerformanceTimeline(): void {
  try {
    if (typeof performance === "undefined") return;

    if (import.meta.env.DEV) {
      console.table({
        measures: performance.getEntriesByType("measure").length,
        marks: performance.getEntriesByType("mark").length,
        resources: performance.getEntriesByType("resource").length,
      });
    }

    performance.clearMeasures?.();
    performance.clearMarks?.();

    if (typeof performance.clearResourceTimings === "function") {
      performance.clearResourceTimings();
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("[performance-cleanup] Failed to clear performance timeline", error);
    }
  }
}
