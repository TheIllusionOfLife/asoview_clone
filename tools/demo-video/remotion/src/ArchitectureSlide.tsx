import type { CSSProperties, ReactNode } from "react";

/**
 * Google Cloud architecture overview, rendered as a native Remotion frame
 * (no PNG export step). Layout is fixed at 1280x800; annotation
 * coordinates in shots.ts reference these same x/y values.
 *
 * Color palette: Google brand blue / red / yellow / green. Not official
 * GCP iconography — the drawio-icon follow-up (plan risk section) can
 * upgrade fidelity later if needed.
 */

const GOOGLE_BLUE = "#4285F4";
const GOOGLE_RED = "#EA4335";
const GOOGLE_YELLOW = "#FBBC04";
const GOOGLE_GREEN = "#34A853";

const bgStyle: CSSProperties = {
  position: "absolute",
  inset: 0,
  background: "linear-gradient(180deg, #ffffff 0%, #f2f3f5 100%)",
  fontFamily: "system-ui, -apple-system, sans-serif",
};

const titleStyle: CSSProperties = {
  position: "absolute",
  top: 32,
  left: 0,
  right: 0,
  textAlign: "center",
  fontSize: 32,
  fontWeight: 700,
  color: "#202124",
  letterSpacing: 0.2,
};

const subtitleStyle: CSSProperties = {
  position: "absolute",
  top: 80,
  left: 0,
  right: 0,
  textAlign: "center",
  fontSize: 16,
  color: "#5f6368",
};

type BoxProps = {
  x: number;
  y: number;
  w: number;
  h: number;
  color: string;
  title: string;
  subtitle?: string;
  children?: ReactNode;
};

const Box = ({ x, y, w, h, color, title, subtitle, children }: BoxProps) => (
  <div
    style={{
      position: "absolute",
      left: x,
      top: y,
      width: w,
      height: h,
      background: "#ffffff",
      border: `2px solid ${color}`,
      borderRadius: 12,
      padding: 12,
      boxShadow: "0 4px 12px rgba(0,0,0,0.06)",
      display: "flex",
      flexDirection: "column",
      gap: 4,
    }}
  >
    <div style={{ fontSize: 15, fontWeight: 700, color }}>{title}</div>
    {subtitle && (
      <div style={{ fontSize: 12, color: "#5f6368", lineHeight: 1.35 }}>{subtitle}</div>
    )}
    {children}
  </div>
);

const SmallLabel = ({ x, y, text }: { x: number; y: number; text: string }) => (
  <div
    style={{
      position: "absolute",
      left: x,
      top: y,
      fontSize: 13,
      color: "#3c4043",
      fontWeight: 500,
    }}
  >
    {text}
  </div>
);

const arrowLine = (x1: number, y1: number, x2: number, y2: number, color = "#80868b") => (
  <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={color} strokeWidth={2} markerEnd="url(#arrow)" />
);

export const ArchitectureSlide = () => {
  return (
    <div style={bgStyle}>
      <div style={titleStyle}>Google Cloud Architecture</div>
      <div style={subtitleStyle}>asoview-clone — dev cluster</div>

      <svg
        style={{ position: "absolute", inset: 0, width: "100%", height: "100%", pointerEvents: "none" }}
        viewBox="0 0 1280 800"
      >
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="6" markerHeight="6" orient="auto">
            <path d="M0,0 L10,5 L0,10 z" fill="#80868b" />
          </marker>
        </defs>
        {arrowLine(640, 155, 640, 170)}
        {arrowLine(420, 388, 180, 520)}
        {arrowLine(500, 388, 410, 520)}
        {arrowLine(640, 388, 640, 520)}
        {arrowLine(780, 388, 850, 520)}
        {arrowLine(900, 388, 1080, 520)}
      </svg>

      <Box
        x={490}
        y={128}
        w={300}
        h={48}
        color={GOOGLE_BLUE}
        title="ユーザー (ブラウザ / PWA)"
      />

      <Box
        x={340}
        y={170}
        w={600}
        h={220}
        color={GOOGLE_RED}
        title="GKE (asoview-clone-dev)"
        subtitle="Namespaces: consumer-web / edge / core-services / ops-services"
      >
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginTop: 8 }}>
          <div style={{ background: "#fce8e6", padding: 8, borderRadius: 6, fontSize: 12 }}>
            <div style={{ fontWeight: 700, color: "#c5221f" }}>consumer-web</div>
            <div>asoview-web (Next.js PWA)</div>
          </div>
          <div style={{ background: "#fce8e6", padding: 8, borderRadius: 6, fontSize: 12 }}>
            <div style={{ fontWeight: 700, color: "#c5221f" }}>edge</div>
            <div>Gateway (WebFlux)</div>
          </div>
          <div style={{ background: "#fce8e6", padding: 8, borderRadius: 6, fontSize: 12 }}>
            <div style={{ fontWeight: 700, color: "#c5221f" }}>core-services</div>
            <div>commerce-core</div>
            <div>ticketing-svc</div>
            <div>reservation-svc</div>
          </div>
        </div>
      </Box>

      <Box x={80} y={520} w={200} h={80} color={GOOGLE_YELLOW} title="Firebase Auth" subtitle="Identity Platform · JWT" />
      <Box x={310} y={520} w={220} h={80} color={GOOGLE_YELLOW} title="Vertex AI Search" subtitle="商品検索 (Discovery Engine)" />
      <Box x={560} y={520} w={180} h={80} color={GOOGLE_GREEN} title="Cloud SQL" subtitle="Postgres — catalog/identity" />
      <Box x={760} y={520} w={180} h={80} color={GOOGLE_GREEN} title="Spanner" subtitle="orders · inventory · reservations" />
      <Box x={960} y={520} w={200} h={80} color={GOOGLE_GREEN} title="Memorystore Redis" subtitle="availability cache" />

      <Box x={80} y={640} w={360} h={100} color={GOOGLE_BLUE} title="Event Pipeline">
        <div style={{ fontSize: 12, color: "#3c4043" }}>
          Outbox → Pub/Sub → analytics-ingest → BigQuery
        </div>
      </Box>
      <Box x={500} y={640} w={360} h={100} color={GOOGLE_BLUE} title="CI / CD">
        <div style={{ fontSize: 12, color: "#3c4043" }}>
          Cloud Build → Artifact Registry → Argo CD → GKE
        </div>
      </Box>
      <Box x={920} y={640} w={240} h={100} color={GOOGLE_BLUE} title="AI / Chatbot">
        <div style={{ fontSize: 12, color: "#3c4043" }}>Gemini API (recs + chat)</div>
      </Box>

      <SmallLabel x={80} y={760} text="© 2026 AsoClone — study project" />
    </div>
  );
};
