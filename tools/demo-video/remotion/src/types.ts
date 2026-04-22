export type Annotation = {
  label: string;
  pointFrom: "top" | "bottom" | "left" | "right";
  tone?: "info" | "highlight";
  x: number;
  y: number;
  width: number;
  height: number;
};

export type ShotManifestEntry = {
  id: string;
  /** Data URL (base64 PNG) for capture shots, empty string for prerendered. */
  image: string;
  /** Name of the Remotion component to mount when `image` is empty. */
  component?: string;
  durationSec: number;
  caption: string;
  annotations: Annotation[];
};

export type ShotManifest = {
  viewport: { width: number; height: number };
  dpr: number;
  fps: number;
  shots: ShotManifestEntry[];
};
