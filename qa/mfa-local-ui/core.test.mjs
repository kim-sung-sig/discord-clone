// Client-side unit tests for the local MFA page's pure helpers. Run: node --test "qa/mfa-local-ui/*.test.mjs"
import assert from "node:assert/strict";
import { test } from "node:test";
import {
  allowedActions, decodeClaims, describeCredential, describeToken, failureMessage, isLive, otpauthUri,
  validateCode, validateCredentials, validateLoginBaseUrl, validateSignup,
} from "../../backend/services/identity/src/main/resources/static/mfa-local-core.js";

const b64url = value => Buffer.from(value).toString("base64url");
const token = claims => `${b64url(JSON.stringify({ alg: "EdDSA", kid: "k" }))}.${b64url(JSON.stringify(claims))}.${b64url("sig")}`;
const NOW = 1_800_000_000;
const SUB = "5d1f4c2e-9c3a-4b7e-8f21-3a9d2c1e0b77";

test("login base url accepts only loopback http origins with an explicit port", () => {
  assert.deepEqual(validateLoginBaseUrl(" http://127.0.0.1:18092/ "), { ok: true, value: "http://127.0.0.1:18092" });
  assert.equal(validateLoginBaseUrl("http://localhost:8080").ok, true);
  for (const bad of ["https://localhost:8080", "http://evil.localhost:8080", "http://localhost", "http://localhost:1",
    "http://192.168.0.10:8080", "http://[::1]:8080", "javascript:alert(1)", "", null, "http://localhost:8080/api"]) {
    assert.equal(validateLoginBaseUrl(bad).ok, false, `should reject ${bad}`);
  }
});

test("credential and signup validation mirror the boot value objects", () => {
  assert.deepEqual(validateCredentials({ email: " Tester@Example.com ", password: "pw" }), { ok: true, value: { email: "tester@example.com", password: "pw" } });
  assert.equal(validateCredentials({ email: "not-an-email", password: "pw" }).ok, false);
  assert.equal(validateCredentials({ email: "a@b.co", password: "   " }).ok, false);
  assert.equal(validateCredentials({ email: "a@b.co", password: "x".repeat(257) }).ok, false);
  const signup = validateSignup({ email: "a@b.co", username: " Local_Tester.01 ", displayName: " 로컬 테스터 ", password: "pw" });
  assert.deepEqual(signup, { ok: true, value: { email: "a@b.co", password: "pw", username: "local_tester.01", displayName: "로컬 테스터" } });
  for (const username of ["ab", ".abc", "abc.", "a".repeat(33), "has space", "ünïcode"]) {
    assert.equal(validateSignup({ email: "a@b.co", username, displayName: "n", password: "pw" }).ok, false, username);
  }
  assert.equal(validateSignup({ email: "a@b.co", username: "abc", displayName: " ", password: "pw" }).ok, false);
});

test("totp code must be exactly six digits after removing whitespace", () => {
  assert.deepEqual(validateCode(" 123 456 "), { ok: true, value: "123456" });
  for (const bad of ["12345", "1234567", "12345a", "", undefined]) assert.equal(validateCode(bad).ok, false, String(bad));
});

test("decodeClaims reads display claims only and rejects malformed or unknown factors", () => {
  assert.deepEqual(decodeClaims(token({ sub: SUB, iat: NOW, exp: NOW + 3600 })), { sub: SUB, iat: NOW, exp: NOW + 3600, amr: [], authTime: null });
  assert.deepEqual(decodeClaims(token({ sub: SUB, exp: NOW + 300, amr: ["totp"], auth_time: NOW })).amr, ["totp"]);
  assert.equal(decodeClaims(token({ sub: SUB, exp: NOW + 300, amr: ["sms"] })), null, "unknown factor is not displayable");
  assert.equal(decodeClaims(token({ exp: NOW + 300 })), null);
  assert.equal(decodeClaims(token({ sub: SUB })), null);
  assert.equal(decodeClaims("a.b"), null);
  assert.equal(decodeClaims(`${b64url("{")}.${b64url("not json")}.x`), null);
  assert.equal(decodeClaims(undefined), null);
});

test("describeToken classifies access, totp and passkey tokens and the fresh MFA window", () => {
  assert.equal(describeToken("", NOW).kind, "none");
  assert.equal(describeToken("garbage", NOW).kind, "invalid");
  const access = describeToken(token({ sub: SUB, exp: NOW + 3600 }), NOW);
  assert.deepEqual([access.kind, access.userId, access.expiresIn, access.freshMfa], ["access", SUB, 3600, false]);
  const totp = describeToken(token({ sub: SUB, exp: NOW + 300, amr: ["totp"], auth_time: NOW }), NOW + 10);
  assert.deepEqual([totp.kind, totp.expiresIn, totp.freshMfa], ["totp", 290, true]);
  assert.match(totp.label, /TOTP.*290초.*최근 MFA/);
  const stale = describeToken(token({ sub: SUB, exp: NOW + 300, amr: ["totp"], auth_time: NOW }), NOW + 300);
  assert.deepEqual([stale.expiresIn, stale.freshMfa], [0, false]);
  assert.match(stale.label, /만료됨/);
  const passkey = describeToken(token({ sub: SUB, exp: NOW + 300, amr: ["passkey"], auth_time: NOW }), NOW);
  assert.equal(passkey.kind, "passkey");
  for (const info of [access, totp, stale, passkey]) assert.doesNotMatch(info.label, /eyJ/, "label never contains token material");
});

test("otpauth uri encodes issuer and account and requires a Base32 secret", () => {
  assert.equal(otpauthUri({ account: SUB, secret: "JBSWY3DPEHPK3PXP" }),
    `otpauth://totp/Discord%20local%20MFA:${SUB}?secret=JBSWY3DPEHPK3PXP&issuer=Discord%20local%20MFA&algorithm=SHA1&digits=6&period=30`);
  assert.throws(() => otpauthUri({ account: SUB, secret: "not base32!" }));
});

test("failure messages are step specific and never echo server text", () => {
  assert.match(failureMessage(401, "login"), /이메일 또는 비밀번호/);
  assert.match(failureMessage(423, "login"), /잠겼/);
  assert.match(failureMessage(429, "login"), /분당 2회/);
  assert.match(failureMessage(409, "enroll"), /이미 TOTP 등록/);
  assert.match(failureMessage(403, "register"), /최근 5분 내/);
  assert.match(failureMessage(401, "complete"), /이미 사용한 코드/);
  assert.match(failureMessage(409, "authenticate"), /등록된 패스키가 없/);
  assert.match(failureMessage(418, "profile"), /^HTTP 418: 요청이 거부/);
  for (const network of [0, undefined, NaN]) assert.match(failureMessage(network, "login"), /CSP\/CORS/);
});

test("allowed actions need a live login token, and registration additionally a fresh MFA token", () => {
  const login = describeToken(token({ sub: SUB, exp: NOW + 60 }), NOW);
  const freshMfa = describeToken(token({ sub: SUB, exp: NOW + 300, amr: ["totp"], auth_time: NOW }), NOW);
  const staleMfa = describeToken(token({ sub: SUB, exp: NOW + 300, amr: ["totp"], auth_time: NOW }), NOW + 300);
  assert.deepEqual([...allowedActions()], ["signup", "login"]);
  assert.deepEqual([...allowedActions({ access: describeToken("", NOW) })], ["signup", "login"]);
  assert.deepEqual([...allowedActions({ access: describeToken("bad", NOW), mfa: freshMfa })], ["signup", "login"], "MFA token alone unlocks nothing");
  assert.deepEqual([...allowedActions({ access: describeToken(token({ sub: SUB, exp: NOW - 1 }), NOW) })], ["signup", "login"]);
  const onlyLogin = allowedActions({ access: login });
  assert.equal(onlyLogin.has("register"), false);
  assert.equal(onlyLogin.has("enroll") && onlyLogin.has("authenticate") && onlyLogin.has("profile") && onlyLogin.has("clear"), true);
  assert.equal(allowedActions({ access: login, mfa: freshMfa }).has("register"), true);
  assert.equal(allowedActions({ access: login, mfa: staleMfa }).has("register"), false);
  assert.deepEqual([isLive(undefined), isLive(login), isLive(staleMfa), isLive(describeToken("bad", NOW))], [false, true, false, false]);
});

test("credential summaries are display-only, truncate labels and never contain key material", () => {
  const entry = { id: "AbCdEfGh1234", label: " 내 노트북 ", createdAt: "2026-09-19T01:02:03.456Z", lastUsedAt: null };
  assert.deepEqual(describeCredential(entry), { id: "AbCdEfGh1234", label: "내 노트북", summary: "내 노트북 · id AbCdEfGh… · 등록 2026-09-19 01:02 UTC · 마지막 사용 사용 기록 없음" });
  assert.match(describeCredential({ id: "x", label: "", createdAt: "junk", lastUsedAt: "2026-09-19T02:00:00Z" }).summary, /\(이름 없음\).*시각 알 수 없음.*2026-09-19 02:00 UTC/);
  assert.equal(describeCredential({ id: "x", label: "L".repeat(80) }).label.length, 64);
  assert.equal(describeCredential({ id: 7, label: "n", publicKey: "SHOULD-NOT-APPEAR" }).id, "");
  assert.doesNotMatch(describeCredential({ id: "x", label: "n", publicKey: "SHOULD-NOT-APPEAR" }).summary, /SHOULD-NOT-APPEAR/);
});

test("passkey management actions: list needs the login token, deletion needs a fresh MFA token", () => {
  const login = describeToken(token({ sub: SUB, exp: NOW + 60 }), NOW);
  const freshMfa = describeToken(token({ sub: SUB, exp: NOW + 300, amr: ["totp"], auth_time: NOW }), NOW);
  const staleMfa = describeToken(token({ sub: SUB, exp: NOW + 300, amr: ["totp"], auth_time: NOW }), NOW + 300);
  assert.equal(allowedActions({ access: login }).has("credentials"), true);
  assert.equal(allowedActions({ access: login }).has("deleteCredential"), false);
  assert.equal(allowedActions({ access: login, mfa: staleMfa }).has("deleteCredential"), false);
  assert.equal(allowedActions({ access: login, mfa: freshMfa }).has("deleteCredential"), true);
  assert.equal(allowedActions({ mfa: freshMfa }).has("credentials"), false, "MFA token alone unlocks nothing");
  assert.match(failureMessage(403, "delete"), /최근 5분 내/);
  assert.match(failureMessage(404, "delete"), /이미 삭제/);
  assert.match(failureMessage(409, "delete"), /마지막 패스키/);
  assert.match(failureMessage(403, "credentials"), /loopback/);
});

test("TOTP recovery actions require a fresh passkey token", () => {
  const access = describeToken(token({ sub: "user", exp: 500, amr: ["totp"], auth_time: 400 }), 450);
  const passkey = describeToken(token({ sub: "user", exp: 500, amr: ["passkey"], auth_time: 400 }), 450);
  assert.equal(allowedActions({ access, mfa: access }).has("unlock"), false);
  assert.equal(allowedActions({ access, mfa: access }).has("reset"), false);
  assert.equal(allowedActions({ access, mfa: passkey }).has("unlock"), true);
  assert.equal(allowedActions({ access, mfa: passkey }).has("reset"), true);
});
