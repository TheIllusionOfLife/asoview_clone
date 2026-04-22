import type { CSSProperties } from "react";

type Props = { text: string };

const wrapper: CSSProperties = {
  position: "absolute",
  bottom: 32,
  left: 0,
  right: 0,
  display: "flex",
  justifyContent: "center",
  pointerEvents: "none",
};

const pill: CSSProperties = {
  backgroundColor: "rgba(11, 11, 11, 0.78)",
  color: "#ffffff",
  padding: "10px 20px",
  borderRadius: 999,
  fontSize: 20,
  fontWeight: 600,
  fontFamily: "system-ui, -apple-system, sans-serif",
  letterSpacing: 0.2,
  boxShadow: "0 8px 24px rgba(0, 0, 0, 0.35)",
  backdropFilter: "blur(6px)",
};

export const Caption = ({ text }: Props) => (
  <div style={wrapper}>
    <div style={pill}>{text}</div>
  </div>
);
