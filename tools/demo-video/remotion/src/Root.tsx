import { Composition } from "remotion";
import manifest from "../../out/shots.inline.json" with { type: "json" };
import { DemoVideo } from "./DemoVideo";
import type { ShotManifest } from "./types";

const CROSSFADE_FRAMES = 12;

export const RemotionRoot = () => {
  const m = manifest as ShotManifest;
  const totalFrames = m.shots.reduce(
    (sum, s) => sum + Math.round(s.durationSec * m.fps),
    0,
  );
  return (
    <>
      <Composition
        id="DemoVideo"
        component={DemoVideo}
        durationInFrames={Math.max(60, totalFrames)}
        fps={m.fps}
        width={m.viewport.width}
        height={m.viewport.height}
        defaultProps={{ manifest: m, crossfadeFrames: CROSSFADE_FRAMES }}
      />
    </>
  );
};
