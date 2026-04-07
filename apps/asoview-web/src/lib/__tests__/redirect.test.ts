import { describe, expect, it } from "vitest";
import { sanitizeNext } from "../redirect";

describe("sanitizeNext", () => {
  it("returns '/' for null", () => {
    expect(sanitizeNext(null)).toBe("/");
  });
  it("returns '/' for undefined", () => {
    expect(sanitizeNext(undefined)).toBe("/");
  });
  it("returns '/' for empty string", () => {
    expect(sanitizeNext("")).toBe("/");
  });
  it("accepts a same-origin relative path", () => {
    expect(sanitizeNext("/products/123")).toBe("/products/123");
  });
  it("accepts a same-origin path with query and hash", () => {
    expect(sanitizeNext("/checkout/abc?provider=stripe#pay")).toBe(
      "/checkout/abc?provider=stripe#pay",
    );
  });
  it("rejects a protocol-relative URL", () => {
    expect(sanitizeNext("//evil.example.com/")).toBe("/");
  });
  it("rejects an absolute http URL", () => {
    expect(sanitizeNext("http://evil.example.com/")).toBe("/");
  });
  it("rejects an absolute https URL", () => {
    expect(sanitizeNext("https://evil.example.com/")).toBe("/");
  });
  it("rejects a javascript: URL", () => {
    expect(sanitizeNext("javascript:alert(1)")).toBe("/");
  });
  it("rejects a path containing a backslash", () => {
    expect(sanitizeNext("/foo\\bar")).toBe("/");
  });
  it("rejects a path starting with backslash", () => {
    expect(sanitizeNext("\\\\evil")).toBe("/");
  });
  it("rejects a path that does not start with /", () => {
    expect(sanitizeNext("foo/bar")).toBe("/");
  });
});
