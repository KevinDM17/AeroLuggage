/**
 * Design tokens compartidos entre CSS (vía @theme en index.css) y JS.
 * Mantener estos valores en sync con --color-* declarados en index.css.
 */

export const tokens = {
  surface0: "#050810",
  surface1: "#0B0E14",
  surface2: "#151b2b",
  surface3: "#1a2235",
  canvas:   "#1e1b4b",
  success:  "#00ff88",
  warning:  "#ffd700",
  danger:   "#ff3b30",
  info:     "#3abff8",
};

/** Color hex según un estado de semáforo. */
export const semaphoreColor = (status) => {
  switch (status) {
    case "green":  return tokens.success;
    case "yellow": return tokens.warning;
    case "red":    return tokens.danger;
    default:       return "#ffffff";
  }
};
