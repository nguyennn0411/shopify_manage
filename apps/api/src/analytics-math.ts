export function weightedMovingAverageForecast(history: number[], horizonDays: number, firstForecastDate: Date) {
  if (history.length < 28) throw new Error('At least 28 daily observations are required');
  const average = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / values.length;
  const daily = Math.max(0, average(history.slice(-7)) * 0.65 + average(history.slice(-28)) * 0.35);
  const predictions = Array.from({ length: horizonDays }, (_, index) => {
    const date = new Date(firstForecastDate.getTime() + index * 86400000);
    return { date: date.toISOString().slice(0, 10), units: Number(daily.toFixed(2)) };
  });
  const errors = history.slice(28).map((actual, index) => Math.abs(actual - average(history.slice(index, index + 28))));
  return { predictions, meanAbsoluteError: errors.length ? average(errors) : null };
}
