import { apiGet, apiPost, USE_MOCK } from "./client";
import {
  mockStartPeriodSim,
  mockStopPeriodSim,
  mockGetPeriodSimState,
  mockStartCollapseSim,
  mockStopCollapseSim,
  mockGetCollapseSimState,
} from "./mock";

/**
 * Simulacion por periodo (5 dias fijos por decision del curso).
 * State shape:
 *   { status: "idle" | "running" | "done", startDate, progress: 0..100 }
 */
export const startPeriodSim = (startDate) =>
  USE_MOCK ? mockStartPeriodSim(startDate) : apiPost("/simulator/period/start", { startDate });

export const stopPeriodSim = () =>
  USE_MOCK ? mockStopPeriodSim() : apiPost("/simulator/period/stop");

export const getPeriodSimState = () =>
  USE_MOCK ? mockGetPeriodSimState() : apiGet("/simulator/period/state");

/**
 * Simulacion hasta colapso.
 * State shape:
 *   { status: "idle" | "running" | "collapsed", startDate, elapsedMs }
 */
export const startCollapseSim = (startDate) =>
  USE_MOCK ? mockStartCollapseSim(startDate) : apiPost("/simulator/collapse/start", { startDate });

export const stopCollapseSim = () =>
  USE_MOCK ? mockStopCollapseSim() : apiPost("/simulator/collapse/stop");

export const getCollapseSimState = () =>
  USE_MOCK ? mockGetCollapseSimState() : apiGet("/simulator/collapse/state");
