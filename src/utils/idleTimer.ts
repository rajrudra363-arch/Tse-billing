export const IDLE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
export const LAST_ACTIVITY_STORAGE_KEY = 'tse_last_activity_at';

export const IDLE_ACTIVITY_EVENTS = [
  'pointerdown',
  'mousemove',
  'keydown',
  'scroll',
  'touchstart',
  'wheel',
] as const;

export interface TimerScheduler {
  setTimeout: (fn: () => void, ms: number) => ReturnType<typeof setTimeout>;
  clearTimeout: (id: ReturnType<typeof setTimeout>) => void;
  now: () => number;
}

const defaultScheduler: TimerScheduler = {
  setTimeout: (fn, ms) => globalThis.setTimeout(fn, ms),
  clearTimeout: (id) => globalThis.clearTimeout(id),
  now: () => Date.now(),
};

export interface IdleSessionTracker {
  start: (initialActivityAt?: number) => void;
  recordActivity: () => void;
  checkExpired: () => boolean;
  stop: () => void;
  getLastActivityAt: () => number;
  getRemainingMs: () => number;
  isStopped: () => boolean;
}

export function isSessionIdleExpired(
  lastActivityAt: number | null | undefined,
  nowMs: number,
  timeoutMs: number = IDLE_TIMEOUT_MS
): boolean {
  if (typeof lastActivityAt !== 'number' || !Number.isFinite(lastActivityAt) || lastActivityAt <= 0) {
    return false;
  }
  return nowMs - lastActivityAt >= timeoutMs;
}

export function createIdleSessionTracker(options: {
  timeoutMs?: number;
  onIdle: () => void;
  scheduler?: TimerScheduler;
}): IdleSessionTracker {
  const timeoutMs = options.timeoutMs ?? IDLE_TIMEOUT_MS;
  const scheduler = options.scheduler ?? defaultScheduler;

  let lastActivityAt = scheduler.now();
  let timerId: ReturnType<typeof setTimeout> | null = null;
  let stopped = true;
  let fired = false;

  const clearPendingTimer = () => {
    if (timerId !== null) {
      scheduler.clearTimeout(timerId);
      timerId = null;
    }
  };

  const triggerIdle = () => {
    if (stopped || fired) return;
    fired = true;
    stopped = true;
    clearPendingTimer();
    options.onIdle();
  };

  const scheduleNextCheck = () => {
    clearPendingTimer();
    if (stopped || fired) return;
    const elapsed = Math.max(0, scheduler.now() - lastActivityAt);
    const remaining = Math.max(0, timeoutMs - elapsed);
    if (remaining === 0) {
      triggerIdle();
      return;
    }
    timerId = scheduler.setTimeout(() => {
      checkExpired();
    }, remaining);
  };

  const checkExpired = (): boolean => {
    if (stopped || fired) return false;
    const elapsed = scheduler.now() - lastActivityAt;
    if (elapsed >= timeoutMs) {
      triggerIdle();
      return true;
    }
    scheduleNextCheck();
    return false;
  };

  const start = (initialActivityAt?: number) => {
    stopped = false;
    fired = false;
    const now = scheduler.now();
    lastActivityAt =
      typeof initialActivityAt === 'number' && Number.isFinite(initialActivityAt) && initialActivityAt > 0
        ? Math.min(initialActivityAt, now)
        : now;
    scheduleNextCheck();
  };

  const recordActivity = () => {
    if (stopped || fired) return;
    lastActivityAt = scheduler.now();
    scheduleNextCheck();
  };

  const stop = () => {
    stopped = true;
    clearPendingTimer();
  };

  return {
    start,
    recordActivity,
    checkExpired,
    stop,
    getLastActivityAt: () => lastActivityAt,
    getRemainingMs: () => Math.max(0, timeoutMs - Math.max(0, scheduler.now() - lastActivityAt)),
    isStopped: () => stopped,
  };
}
