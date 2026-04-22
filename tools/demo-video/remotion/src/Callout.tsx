import type { CSSProperties } from "react";
import type { Annotation } from "./types";

const COLORS = {
  highlight: { border: "#c8553d", fill: "rgba(200, 85, 61, 0.12)", label: "#c8553d" },
  info: { border: "#1f6feb", fill: "rgba(31, 111, 235, 0.10)", label: "#1f6feb" },
} as const;

const LABEL_GAP = 12;
const LABEL_PADDING_X = 10;
const LABEL_PADDING_Y = 6;
const LABEL_FONT = 15;
// CJK glyphs are ~1.5-2x wider than Latin at the same font-size; use a wide
// estimate so Japanese labels do not clip in their pill. A per-character
// CJK-vs-Latin detector would be more accurate, but over-sizing empty pixels
// is cheaper than under-sizing and truncating mid-word.
const CHAR_WIDTH = 15;

type Props = { annotation: Annotation; progress: number };

export const Callout = ({ annotation, progress }: Props) => {
  const tone = COLORS[annotation.tone ?? "info"];
  const { x, y, width, height, label, pointFrom } = annotation;

  const boxStyle: CSSProperties = {
    position: "absolute",
    left: x,
    top: y,
    width,
    height,
    border: `2px solid ${tone.border}`,
    backgroundColor: tone.fill,
    borderRadius: 6,
    boxShadow: `0 0 0 4px ${tone.fill}`,
    opacity: progress,
    transform: `scale(${0.96 + 0.04 * progress})`,
    transformOrigin: "center",
  };

  const estimatedLabelWidth = Math.max(48, label.length * CHAR_WIDTH) + LABEL_PADDING_X * 2;
  const estimatedLabelHeight = LABEL_FONT + LABEL_PADDING_Y * 2;
  const labelPosition = resolveLabelPosition({
    pointFrom,
    x,
    y,
    width,
    height,
    labelWidth: estimatedLabelWidth,
    labelHeight: estimatedLabelHeight,
  });

  const labelStyle: CSSProperties = {
    position: "absolute",
    left: labelPosition.left,
    top: labelPosition.top,
    backgroundColor: tone.label,
    color: "#ffffff",
    padding: `${LABEL_PADDING_Y}px ${LABEL_PADDING_X}px`,
    borderRadius: 6,
    fontSize: LABEL_FONT,
    fontWeight: 600,
    letterSpacing: 0.1,
    fontFamily: "system-ui, -apple-system, sans-serif",
    boxShadow: "0 4px 10px rgba(0,0,0,0.18)",
    opacity: progress,
    transform: `translateY(${(1 - progress) * 4}px)`,
    whiteSpace: "nowrap",
  };

  return (
    <>
      <div style={boxStyle} />
      <div style={labelStyle}>{label}</div>
    </>
  );
};

// Place the label on the side indicated by pointFrom, but flip to the opposite
// side if it would leave the 1280×800 frame. Edge-safety matters more than
// direction — a label outside the frame is invisible.
function resolveLabelPosition({
  pointFrom,
  x,
  y,
  width,
  height,
  labelWidth,
  labelHeight,
}: {
  pointFrom: Annotation["pointFrom"];
  x: number;
  y: number;
  width: number;
  height: number;
  labelWidth: number;
  labelHeight: number;
}): { left: number; top: number } {
  const frameW = 1280;
  const frameH = 800;
  const candidates: Record<Annotation["pointFrom"], { left: number; top: number }> = {
    top: {
      left: clamp(x + width / 2 - labelWidth / 2, 8, frameW - labelWidth - 8),
      top: y - labelHeight - LABEL_GAP,
    },
    bottom: {
      left: clamp(x + width / 2 - labelWidth / 2, 8, frameW - labelWidth - 8),
      top: y + height + LABEL_GAP,
    },
    left: {
      left: x - labelWidth - LABEL_GAP,
      top: clamp(y + height / 2 - labelHeight / 2, 8, frameH - labelHeight - 8),
    },
    right: {
      left: x + width + LABEL_GAP,
      top: clamp(y + height / 2 - labelHeight / 2, 8, frameH - labelHeight - 8),
    },
  };

  const preferred = candidates[pointFrom];
  if (fits(preferred, labelWidth, labelHeight)) return preferred;

  const flipMap: Record<Annotation["pointFrom"], Annotation["pointFrom"]> = {
    top: "bottom",
    bottom: "top",
    left: "right",
    right: "left",
  };
  const flipped = candidates[flipMap[pointFrom]];
  if (fits(flipped, labelWidth, labelHeight)) return flipped;

  // Last resort: clamp inside the frame.
  return {
    left: clamp(preferred.left, 8, frameW - labelWidth - 8),
    top: clamp(preferred.top, 8, frameH - labelHeight - 8),
  };
}

function fits(
  p: { left: number; top: number },
  labelWidth: number,
  labelHeight: number,
): boolean {
  return p.left >= 0 && p.top >= 0 && p.left + labelWidth <= 1280 && p.top + labelHeight <= 800;
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}
