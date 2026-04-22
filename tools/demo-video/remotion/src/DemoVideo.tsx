import { AbsoluteFill, Img, Sequence, interpolate, useCurrentFrame } from "remotion";
import type { ShotManifest, ShotManifestEntry } from "./types";
import { Callout } from "./Callout";
import { Caption } from "./Caption";

type Props = {
  manifest: ShotManifest;
  crossfadeFrames: number;
};

export const DemoVideo = ({ manifest, crossfadeFrames }: Props) => {
  const fps = manifest.fps;
  let offset = 0;
  const slots = manifest.shots.map((shot) => {
    const durationInFrames = Math.round(shot.durationSec * fps);
    const entry = { shot, from: offset, durationInFrames };
    offset += durationInFrames;
    return entry;
  });

  return (
    <AbsoluteFill style={{ backgroundColor: "#0b0b0b" }}>
      {slots.map(({ shot, from, durationInFrames }, idx) => (
        <Sequence key={shot.id} from={from} durationInFrames={durationInFrames + crossfadeFrames}>
          <Shot
            shot={shot}
            durationInFrames={durationInFrames}
            crossfadeFrames={crossfadeFrames}
            isFirst={idx === 0}
          />
        </Sequence>
      ))}
    </AbsoluteFill>
  );
};

type ShotProps = {
  shot: ShotManifestEntry;
  durationInFrames: number;
  crossfadeFrames: number;
  isFirst: boolean;
};

const Shot = ({ shot, durationInFrames, crossfadeFrames, isFirst }: ShotProps) => {
  const frame = useCurrentFrame();
  // Crossfade in over the first `crossfadeFrames`; stay opaque until the end,
  // then fade out over the trailing `crossfadeFrames` (which overlap the next
  // sequence because we extended this sequence's duration by crossfadeFrames).
  const fadeIn = isFirst
    ? 1
    : interpolate(frame, [0, crossfadeFrames], [0, 1], { extrapolateRight: "clamp" });
  const fadeOut = interpolate(
    frame,
    [durationInFrames, durationInFrames + crossfadeFrames],
    [1, 0],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
  );
  const opacity = Math.min(fadeIn, fadeOut);

  // Annotation appears after the crossfade-in completes so it doesn't flash
  // against a half-faded screenshot.
  const annotationStart = crossfadeFrames + 4;
  const annotationVisible = frame >= annotationStart;
  const annotationProgress = interpolate(
    frame,
    [annotationStart, annotationStart + 10],
    [0, 1],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
  );

  return (
    <AbsoluteFill style={{ opacity }}>
      <Img src={shot.image} style={{ width: "100%", height: "100%" }} />
      {annotationVisible &&
        shot.annotations.map((annotation, i) => (
          <Callout
            key={`${shot.id}-annotation-${i}`}
            annotation={annotation}
            progress={annotationProgress}
          />
        ))}
      <Caption text={shot.caption} />
    </AbsoluteFill>
  );
};
