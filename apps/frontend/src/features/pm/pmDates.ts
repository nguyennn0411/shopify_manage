// Pure date helpers for the calendar and Gantt views.
export const MS_PER_DAY = 86_400_000;

export type DateCell = { iso: string; day: number; inMonth: boolean };

export function toIso(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function parseIso(iso: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  return date;
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date.getTime());
  next.setDate(next.getDate() + days);
  return next;
}

/** Monday-based weekday index (0 = Monday … 6 = Sunday). */
export function mondayIndex(date: Date): number {
  return (date.getDay() + 6) % 7;
}

/** Six-week grid covering the given month, starting on a Monday. */
export function buildMonthGrid(year: number, month: number): DateCell[] {
  const first = new Date(year, month, 1);
  const gridStart = addDays(first, -mondayIndex(first));
  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(gridStart, index);
    return { iso: toIso(date), day: date.getDate(), inMonth: date.getMonth() === month };
  });
}

export function shiftMonth(year: number, month: number, delta: number): { year: number; month: number } {
  const date = new Date(year, month + delta, 1);
  return { year: date.getFullYear(), month: date.getMonth() };
}

export const MONTH_LABELS = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6',
  'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'];

export const WEEKDAY_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

export type BarPosition = { left: number; width: number; days: number };

/**
 * Position a task bar inside a Gantt track, as percentages of the whole range.
 * A task with only a due date is treated as a one-day bar.
 */
export function barPosition(startsOn: string | null, dueOn: string | null, rangeStart: Date, rangeEnd: Date): BarPosition | null {
  const start = startsOn ? parseIso(startsOn) : null;
  const due = dueOn ? parseIso(dueOn) : null;
  if (!start && !due) return null;
  const from = start ?? due!;
  const to = due ?? start!;
  const totalDays = Math.max(1, Math.round((rangeEnd.getTime() - rangeStart.getTime()) / MS_PER_DAY));
  const startOffset = Math.round((from.getTime() - rangeStart.getTime()) / MS_PER_DAY);
  const spanDays = Math.max(1, Math.round((to.getTime() - from.getTime()) / MS_PER_DAY) + 1);
  const clampedStart = Math.min(Math.max(startOffset, 0), totalDays);
  const clampedEnd = Math.min(Math.max(startOffset + spanDays, clampedStart + 1), totalDays);
  return { left: (clampedStart / totalDays) * 100, width: ((clampedEnd - clampedStart) / totalDays) * 100, days: spanDays };
}

/** Day offsets for the Gantt header, one label per week. */
export function weekOffsets(rangeStart: Date, weekCount: number): string[] {
  return Array.from({ length: weekCount }, (_, index) => toIso(addDays(rangeStart, index * 7)));
}

export function isOverdue(dueOn: string | null, done: boolean, today = new Date()): boolean {
  if (!dueOn || done) return false;
  const due = parseIso(dueOn);
  if (!due) return false;
  return due.getTime() < new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
}
