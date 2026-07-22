import { useState, useEffect } from "react";

const fmtOptions = {
  date: {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  },
  time: {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    timeZone: "UTC",
  },
};

function getZoneDate(gmtOffset) {
  return new Date(Date.now() + gmtOffset * 3600000);
}

export default function RealtimeClock({ gmtOffset }) {
  const hasOffset = typeof gmtOffset === "number" && Number.isFinite(gmtOffset);
  const [now, setNow] = useState(hasOffset ? getZoneDate(gmtOffset) : new Date());

  useEffect(() => {
    const id = setInterval(
      () => setNow(hasOffset ? getZoneDate(gmtOffset) : new Date()),
      1000
    );
    return () => clearInterval(id);
  }, [hasOffset, gmtOffset]);

  const date = now.toLocaleDateString("es-PE", hasOffset ? fmtOptions.date : {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  const time = now.toLocaleTimeString("es-PE", hasOffset ? fmtOptions.time : {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });

  const gmtLabel = hasOffset
    ? `GMT${gmtOffset >= 0 ? "+" : ""}${gmtOffset}`
    : null;

  return (
    <span className="text-xs text-slate-200 tabular-nums select-none">
      {date} &middot; {time}{gmtLabel && <> &middot; {gmtLabel}</>}
    </span>
  );
}
