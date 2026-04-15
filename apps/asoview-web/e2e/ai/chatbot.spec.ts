import { expect, test } from "@playwright/test";

// POSTs to /v1/chat and asserts a non-empty Japanese reply. The ChatController
// has a 15 s timeout + typed "unavailable" fallbacks for timeout / error, so
// this test distinguishes real Gemini responses from the fallback copy by
// checking for at least 50 characters AND that the text is NOT one of the
// known fallback messages.
test.describe("AI chatbot", () => {
  test.skip(process.env.ASOVIEW_AI_ENABLED !== "true", "AI disabled — skipping chatbot check");

  const FALLBACK_MESSAGES = [
    "AIチャット機能は現在利用できません。",
    "応答に時間がかかっています。しばらくしてから再度お試しください。",
    "申し訳ございません。現在チャットをご利用いただけません。",
    "回答を生成できませんでした。もう一度お試しください。",
  ];

  test("chat endpoint returns a Gemini-shaped reply", async ({ request }) => {
    const apiBase = process.env.API_BASE_URL ?? "https://asoview-clone-dev.duckdns.org/api";
    const response = await request.post(`${apiBase}/v1/chat`, {
      data: {
        message: "箱根で家族4人向けの温泉日帰り体験を教えてください。",
      },
    });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.reply).toBeTruthy();
    expect(body.reply.length).toBeGreaterThanOrEqual(50);
    expect(FALLBACK_MESSAGES).not.toContain(body.reply);
  });
});
