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
  image: string;
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
