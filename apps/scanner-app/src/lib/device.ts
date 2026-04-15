import * as SecureStore from "expo-secure-store";

const KEY = "scanner.deviceId";

export async function getDeviceId(): Promise<string> {
  const existing = await SecureStore.getItemAsync(KEY);
  if (existing) return existing;
  const id = randomUUID();
  await SecureStore.setItemAsync(KEY, id);
  return id;
}

function randomUUID(): string {
  // RN doesn't have crypto.randomUUID on older engines, so fall back to a
  // v4-like generator. The device ID is for audit correlation, not security,
  // so Math.random entropy is acceptable here.
  if (
    typeof globalThis.crypto !== "undefined" &&
    typeof globalThis.crypto.randomUUID === "function"
  ) {
    return globalThis.crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export { randomUUID };
