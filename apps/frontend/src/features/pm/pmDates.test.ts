import { describe, expect, it } from 'vitest';
import { addDays, barPosition, buildMonthGrid, isOverdue, mondayIndex, parseIso, toIso } from './pmDates';

describe('buildMonthGrid', () => {
  it('covers 42 days starting on a Monday', () => {
    const grid = buildMonthGrid(2026, 10);
    expect(grid).toHaveLength(42);
    expect(new Date(grid[0].iso + 'T00:00:00').getDay()).toBe(1);
    expect(mondayIndex(new Date(grid[0].iso + 'T00:00:00'))).toBe(0);
  });

  it('marks days outside the requested month', () => {
    const grid = buildMonthGrid(2026, 10);
    expect(grid.some(cell => cell.inMonth)).toBe(true);
    expect(grid.some(cell => !cell.inMonth)).toBe(true);
  });
});

describe('toIso and parseIso', () => {
  it('round-trips a date', () => {
    const date = new Date(2026, 4, 9);
    expect(parseIso(toIso(date))?.getTime()).toBe(date.getTime());
  });

  it('rejects malformed input', () => {
    expect(parseIso('2026-13-01')).toBeNull();
    expect(parseIso('not-a-date')).toBeNull();
  });
});

describe('barPosition', () => {
  const rangeStart = new Date(2026, 0, 1);
  const rangeEnd = new Date(2026, 0, 31);

  it('spans a task across the range as percentages', () => {
    const position = barPosition('2026-01-05', '2026-01-09', rangeStart, rangeEnd);
    expect(position).not.toBeNull();
    expect(position!.days).toBe(5);
    expect(position!.left).toBeCloseTo((4 / 30) * 100, 4);
  });

  it('treats a missing start as a one-day bar on the due date', () => {
    const position = barPosition(null, '2026-01-09', rangeStart, rangeEnd);
    expect(position!.days).toBe(1);
  });

  it('returns null when no date is known', () => {
    expect(barPosition(null, null, rangeStart, rangeEnd)).toBeNull();
  });

  it('clamps bars that fall outside the range', () => {
    const position = barPosition('2025-11-01', '2026-02-01', rangeStart, rangeEnd);
    expect(position!.left).toBe(0);
    expect(position!.left + position!.width).toBeLessThanOrEqual(100.0001);
  });
});

describe('isOverdue', () => {
  const today = new Date(2026, 0, 15);

  it('flags a past due date that is not done', () => {
    expect(isOverdue('2026-01-10', false, today)).toBe(true);
  });

  it('ignores completed and future tasks', () => {
    expect(isOverdue('2026-01-10', true, today)).toBe(false);
    expect(isOverdue('2026-01-20', false, today)).toBe(false);
    expect(isOverdue(null, false, today)).toBe(false);
  });
});

describe('addDays', () => {
  it('moves across a month boundary', () => {
    expect(toIso(addDays(new Date(2026, 0, 31), 1))).toBe('2026-02-01');
  });
});
