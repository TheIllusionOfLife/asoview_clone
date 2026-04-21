import { CameraView, useCameraPermissions } from "expo-camera";
import { useRouter } from "expo-router";
import { signOut } from "firebase/auth";
import { useEffect, useRef, useState } from "react";
import { ActivityIndicator, Button, StyleSheet, Text, TextInput, View } from "react-native";
import { isValidQrFormat, type RedeemResult, redeem } from "../src/lib/api";
import { getDeviceId } from "../src/lib/device";
import { firebaseAuth } from "../src/lib/firebase";

const SCAN_COOLDOWN_MS = 1500;

export default function Scan() {
  const router = useRouter();
  const [permission, requestPermission] = useCameraPermissions();
  const [venueId, setVenueId] = useState<string>("");
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [lastResult, setLastResult] = useState<RedeemResult | null>(null);
  const lastScanRef = useRef<{ payload: string; at: number } | null>(null);

  useEffect(() => {
    getDeviceId()
      .then(setDeviceId)
      .catch((e) => {
        setLastResult({
          kind: "network_error",
          message: `Device init failed: ${e instanceof Error ? e.message : "unknown"}`,
        });
      });
  }, []);

  async function handleScanned({ data }: { data: string }) {
    if (busy) return;
    const now = Date.now();
    if (lastScanRef.current && now - lastScanRef.current.at < SCAN_COOLDOWN_MS) {
      return;
    }
    lastScanRef.current = { payload: data, at: now };
    if (!isValidQrFormat(data)) {
      setLastResult({ kind: "denied", code: "FORMAT_INVALID", status: 400 });
      return;
    }
    if (!venueId) {
      setLastResult({ kind: "denied", code: "VENUE_NOT_SELECTED", status: 400 });
      return;
    }
    if (!deviceId) return;
    setBusy(true);
    try {
      const result = await redeem(data, deviceId, venueId);
      setLastResult(result);
    } catch (e) {
      // redeem() is designed to never throw, but belt-and-suspenders: any
      // unexpected throw must not leave the scanner stuck on busy.
      setLastResult({
        kind: "network_error",
        message: e instanceof Error ? e.message : "unexpected error",
      });
    } finally {
      setBusy(false);
    }
  }

  if (!permission) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator />
      </View>
    );
  }
  if (!permission.granted) {
    return (
      <View style={styles.center}>
        <Text style={styles.title}>Camera permission required</Text>
        <Button title="Grant" onPress={requestPermission} />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <View style={styles.header}>
        <TextInput
          style={styles.venueInput}
          placeholder="Venue ID"
          autoCapitalize="none"
          value={venueId}
          onChangeText={setVenueId}
        />
        <Button
          title="Sign out"
          onPress={async () => {
            await signOut(firebaseAuth());
            router.replace("/sign-in");
          }}
        />
      </View>

      <View style={styles.cameraWrap}>
        <CameraView
          style={StyleSheet.absoluteFill}
          facing="back"
          barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
          onBarcodeScanned={busy ? undefined : handleScanned}
        />
      </View>

      <View style={styles.resultPanel}>
        {busy && <ActivityIndicator />}
        {!busy && lastResult && <ResultBadge result={lastResult} />}
        {!busy && !lastResult && <Text style={styles.hint}>Point camera at a ticket QR.</Text>}
      </View>
    </View>
  );
}

function ResultBadge({ result }: { result: RedeemResult }) {
  if (result.kind === "network_error") {
    return (
      <Text style={[styles.badge, styles.badgeWarn]}>Connection required — {result.message}</Text>
    );
  }
  if (result.kind === "ok") {
    if (result.outcome === "REDEEMED") {
      return (
        <Text style={[styles.badge, styles.badgeOk]}>
          {result.replayed ? "REDEEMED (replay)" : "REDEEMED"}
        </Text>
      );
    }
    if (result.outcome === "ALREADY_USED") {
      return (
        <Text style={[styles.badge, styles.badgeWarn]}>
          ALREADY USED{result.usedAt ? ` at ${result.usedAt}` : ""}
        </Text>
      );
    }
    if (result.outcome === "RATE_LIMITED") {
      return <Text style={[styles.badge, styles.badgeWarn]}>Too many scans — slow down</Text>;
    }
    return <Text style={[styles.badge, styles.badgeDeny]}>Ticket not valid at this gate</Text>;
  }
  if (result.code === "FORMAT_INVALID") {
    return <Text style={[styles.badge, styles.badgeDeny]}>Unknown code format</Text>;
  }
  if (result.code === "VENUE_NOT_SELECTED") {
    return <Text style={[styles.badge, styles.badgeWarn]}>Select a venue first</Text>;
  }
  if (result.status === 429) {
    return <Text style={[styles.badge, styles.badgeWarn]}>Rate limited — slow down</Text>;
  }
  if (result.status === 401 || result.status === 403) {
    return <Text style={[styles.badge, styles.badgeDeny]}>Not authorized to scan</Text>;
  }
  return <Text style={[styles.badge, styles.badgeDeny]}>Ticket not valid at this gate</Text>;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  loading: { flex: 1, alignItems: "center", justifyContent: "center" },
  center: { flex: 1, alignItems: "center", justifyContent: "center", gap: 12 },
  title: { fontSize: 18, fontWeight: "600" },
  header: { flexDirection: "row", gap: 8, padding: 12, alignItems: "center" },
  venueInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: "#ccc",
    borderRadius: 6,
    padding: 8,
  },
  cameraWrap: { flex: 1, backgroundColor: "#000" },
  resultPanel: { padding: 16, minHeight: 80, alignItems: "center", justifyContent: "center" },
  hint: { color: "#666" },
  badge: {
    fontSize: 18,
    fontWeight: "700",
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    color: "#fff",
    overflow: "hidden",
  },
  badgeOk: { backgroundColor: "#2e7d32" },
  badgeWarn: { backgroundColor: "#f9a825", color: "#1a1a1a" },
  badgeDeny: { backgroundColor: "#c62828" },
});
