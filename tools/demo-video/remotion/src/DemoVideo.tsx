import type { ReactElement } from "react";
import { AbsoluteFill, Img, Sequence, interpolate, useCurrentFrame } from "remotion";
import { ArchitectureSlide } from "./ArchitectureSlide";
import type { ShotManifest, ShotManifestEntry } from "./types";
import { Callout } from "./Callout";
import { Caption } from "./Caption";

type Props = {
  manifest: ShotManifest;
  crossfadeFrames: number;
};

const PRERENDERED: Record<string, () => ReactElement> = {
  ArchitectureSlide: () => <ArchitectureSlide />,
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
            frameWidth={manifest.viewport.width}
            frameHeight={manifest.viewport.height}
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
  frameWidth: number;
  frameHeight: number;
};

const Shot = ({
  shot,
  durationInFrames,
  crossfadeFrames,
  isFirst,
  frameWidth,
  frameHeight,
}: ShotProps) => {
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

  const annotationStart = crossfadeFrames + 4;
  const annotationVisible = frame >= annotationStart;
  const annotationProgress = interpolate(
    frame,
    [annotationStart, annotationStart + 10],
    [0, 1],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
  );

  // Prerendered shot: mount the named component. The mobile shot is a
  // capture but renders inside a phone-frame mockup so it reads as "mobile"
  // against the 1280x800 video frame.
  const isMobile = shot.id.includes("mobile");

  return (
    <AbsoluteFill style={{ opacity }}>
      {shot.component ? (
        PRERENDERED[shot.component]?.() ?? null
      ) : isMobile ? (
        <MobileFrame src={shot.image} />
      ) : (
        <Img src={shot.image} style={{ width: "100%", height: "100%" }} />
      )}
      {annotationVisible &&
        shot.annotations.map((annotation, i) => (
          <Callout
            key={`${shot.id}-annotation-${i}`}
            annotation={annotation}
            progress={annotationProgress}
            frameWidth={frameWidth}
            frameHeight={frameHeight}
          />
        ))}
      <Caption text={shot.caption} />
    </AbsoluteFill>
  );
};

// Phone-frame mockup for mobile captures. The screenshot is 390x844 (iPhone
// 14 viewport); display it scaled into a centered portrait rectangle with a
// rounded border so it visually reads as "a phone" against the 1280x800 frame.
const MobileFrame = ({ src }: { src: string }) => {
  const targetH = 720;
  const scale = targetH / 844;
  const targetW = Math.round(390 * scale);
  return (
    <AbsoluteFill
      style={{
        background: "linear-gradient(180deg, #eceff1 0%, #cfd8dc 100%)",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div
        style={{
          width: targetW + 16,
          height: targetH + 16,
          borderRadius: 36,
          background: "#111",
          padding: 8,
          boxShadow: "0 20px 40px rgba(0,0,0,0.25)",
        }}
      >
        <Img
          src={src}
          style={{
            width: targetW,
            height: targetH,
            borderRadius: 28,
            display: "block",
            objectFit: "cover",
          }}
        />
      </div>
    </AbsoluteFill>
  );
};
