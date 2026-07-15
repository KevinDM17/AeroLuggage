import { useState, useEffect } from "react";

export default function RealtimeClock() {
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const date = now.toLocaleDateString("es-PE", {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  const time = now.toLocaleTimeString("es-PE", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });

  return (
    <span className="text-xs text-slate-200 tabular-nums select-none">
      {date} &middot; {time}
    </span>
  );
}
