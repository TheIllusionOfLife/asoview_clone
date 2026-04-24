import { Composition } from "remotion";
import manifest from "../../out/operator.shots.inline.json" with { type: "json" };
import { DemoVideo } from "./DemoVideo";
import type { ShotManifest } from "./types";

const CROSSFADE_FRAMES = 12;

// Separate root + composition for the UraKata Reservation operator video.
// Reuses the DemoVideo component (captions + crossfades + annotations) but
// reads its own manifest so the consumer and operator flows can be rendered
// independently without cross-contamination.
export const OperatorRoot = () => {
  const m = manifest as ShotManifest;
  const totalFrames = m.shots.reduce(
    (sum, s) => sum + Math.round(s.durationSec * m.fps),
    0,
  );
  return (
    <Composition
      id="OperatorDemoVideo"
      component={DemoVideo}
      durationInFrames={Math.max(1, totalFrames)}
      fps={m.fps}
      width={m.viewport.width}
      height={m.viewport.height}
      defaultProps={{ manifest: m, crossfadeFrames: CROSSFADE_FRAMES }}
    />
  );
};
