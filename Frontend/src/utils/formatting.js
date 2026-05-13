export const formatTime = (timeInMinutes) => {
  const hours = Math.floor(timeInMinutes / 60);
  const minutes = timeInMinutes % 60;
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
};

export const formatElapsedHMS = (ms) => {
  const totalSec = Math.floor((ms ?? 0) / 1000);
  const hours = Math.floor(totalSec / 3600);
  const minutes = Math.floor((totalSec % 3600) / 60);
  const seconds = totalSec % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
};

export const formatTimeZoneOffset = (value = new Date()) => {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return "--";

  const offsetMinutes = -date.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? "+" : "-";
  const absoluteMinutes = Math.abs(offsetMinutes);
  const hours = Math.floor(absoluteMinutes / 60);
  const minutes = absoluteMinutes % 60;

  return `GMT${sign}${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
};

export const formatDateTimeDisplay = (value) => {
  if (!value) return { date: "--", time: "--:--:--", timeZone: "--" };
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return { date: "--", time: "--:--:--", timeZone: "--" };

  return {
    date: date.toLocaleDateString("es-PE", {
      day: "2-digit",
      month: "2-digit",
      year: "2-digit",
    }),
    time: date.toLocaleTimeString("es-PE", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    }),
    timeZone: formatTimeZoneOffset(date),
  };
};

export const formatNumber = (num) => {
  return new Intl.NumberFormat('en-US').format(num);
};

export const capitalize = (str) => {
  if (!str) return '';
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
};
