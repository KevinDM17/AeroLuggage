import { memo } from "react";
import RealtimeClock from "../ui/RealtimeClock";

const SimTopBar = memo(function SimTopBar({ left, children }) {
  return (
    <div className="fixed top-0 left-0 right-0 z-[9998] h-8 flex items-center bg-black/40 backdrop-blur-sm border-b border-white/5 px-3">
      <div className="flex-1 flex items-center gap-3 min-w-0">
        {left}
      </div>
      <RealtimeClock />
      <div className="flex-1 flex items-center justify-end gap-2">
        {children}
      </div>
    </div>
  );
});

export default SimTopBar;
