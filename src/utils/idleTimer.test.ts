import test from 'node:test';
import assert from 'node:assert/strict';
import {
  IDLE_TIMEOUT_MS,
  createIdleSessionTracker,
  isSessionIdleExpired,
  type TimerScheduler,
} from './idleTimer';

function createFakeScheduler(initialNow = 1_000_000) {
  let nowMs = initialNow;
  let nextId = 1;
  const timers = new Map<number, { fireAt: number; fn: () => void }>();

  const scheduler: TimerScheduler = {
    now: () => nowMs,
    setTimeout: (fn, ms) => {
      const id = nextId++;
      timers.set(id, { fireAt: nowMs + Math.max(0, ms), fn });
      return id as unknown as ReturnType<typeof setTimeout>;
    },
    clearTimeout: (id) => {
      timers.delete(id as unknown as number);
    },
  };

  const advanceBy = (ms: number) => {
    const target = nowMs + ms;
    while (true) {
      let earliestId: number | null = null;
      let earliestFireAt = Infinity;
      for (const [id, entry] of timers.entries()) {
        if (entry.fireAt <= target && entry.fireAt < earliestFireAt) {
          earliestFireAt = entry.fireAt;
          earliestId = id;
        }
      }
      if (earliestId === null) break;
      const entry = timers.get(earliestId)!;
      timers.delete(earliestId);
      nowMs = entry.fireAt;
      entry.fn();
    }
    nowMs = target;
  };

  return { scheduler, advanceBy, pendingCount: () => timers.size };
}

test('IDLE_TIMEOUT_MS defaults to 30 minutes', () => {
  assert.equal(IDLE_TIMEOUT_MS, 30 * 60 * 1000);
});

test('triggers onIdle automatically after 30 minutes of inactivity', () => {
  const fake = createFakeScheduler(1_700_000_000_000);
  let idleCount = 0;

  const tracker = createIdleSessionTracker({
    scheduler: fake.scheduler,
    onIdle: () => {
      idleCount++;
    },
  });

  tracker.start();
  fake.advanceBy(29 * 60 * 1000);
  assert.equal(idleCount, 0);
  assert.equal(tracker.getRemainingMs(), 60 * 1000);

  fake.advanceBy(60 * 1000);
  assert.equal(idleCount, 1);
  assert.equal(tracker.isStopped(), true);
  assert.equal(fake.pendingCount(), 0);
});

test('user activity resets the 30-minute inactivity countdown', () => {
  const fake = createFakeScheduler(1_700_000_000_000);
  let idleCount = 0;

  const tracker = createIdleSessionTracker({
    scheduler: fake.scheduler,
    onIdle: () => {
      idleCount++;
    },
  });

  tracker.start();
  fake.advanceBy(25 * 60 * 1000);
  assert.equal(idleCount, 0);

  // User interacts at 25m -> resets timer for another full 30m
  tracker.recordActivity();
  assert.equal(tracker.getRemainingMs(), IDLE_TIMEOUT_MS);

  fake.advanceBy(29 * 60 * 1000);
  assert.equal(idleCount, 0);

  fake.advanceBy(60 * 1000);
  assert.equal(idleCount, 1);
});

test('stop prevents future logout callbacks and clears pending timers', () => {
  const fake = createFakeScheduler(1_700_000_000_000);
  let idleCount = 0;

  const tracker = createIdleSessionTracker({
    scheduler: fake.scheduler,
    onIdle: () => {
      idleCount++;
    },
  });

  tracker.start();
  assert.equal(fake.pendingCount(), 1);

  tracker.stop();
  assert.equal(fake.pendingCount(), 0);

  fake.advanceBy(60 * 60 * 1000);
  assert.equal(idleCount, 0);
});

test('checkExpired and initialActivityAt immediately expire throttled or stale sessions', () => {
  const fake = createFakeScheduler(1_700_000_000_000);
  let idleCount = 0;

  const tracker = createIdleSessionTracker({
    scheduler: fake.scheduler,
    onIdle: () => {
      idleCount++;
    },
  });

  // Started with a last-activity timestamp from 31 minutes ago
  tracker.start(1_700_000_000_000 - 31 * 60 * 1000);
  assert.equal(idleCount, 1);
  assert.equal(tracker.isStopped(), true);

  assert.equal(isSessionIdleExpired(1000, 1000 + IDLE_TIMEOUT_MS), true);
  assert.equal(isSessionIdleExpired(1000, 1000 + IDLE_TIMEOUT_MS - 1), false);
  assert.equal(isSessionIdleExpired(null, 5000), false);
});
