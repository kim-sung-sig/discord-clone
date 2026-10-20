// Pure helpers for the local MFA page. No DOM, no fetch, no storage. Runs in the browser (ES module) and in Node tests.
// Nothing here verifies a token: decodeClaims/describeToken only read claims for display; the servers verify signatures.

export const LOOPBACK_BASE_URL = /^http:\/\/(127\.0\.0\.1|localhost):\d{2,5}$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const USERNAME = /^[a-z0-9_](?:[a-z0-9_.]{1,30}[a-z0-9_])$/;
const CODE = /^\d{6}$/;
const FACTORS = new Set(["totp", "passkey"]);
export const FRESH_MFA_SECONDS = 300;
export const TOTP_ISSUER = "Discord local MFA";

const text = value => String(value ?? "").trim();

export function validateLoginBaseUrl(raw) {
  const value = text(raw).replace(/\/$/, "");
  if (!LOOPBACK_BASE_URL.test(value)) {
    return { ok: false, reason: "로그인 서버 주소는 http://127.0.0.1:<포트> 또는 http://localhost:<포트> 형식만 허용합니다." };
  }
  return { ok: true, value };
}

export function validateCredentials({ email, password }) {
  const normalizedEmail = text(email).toLowerCase();
  if (!EMAIL.test(normalizedEmail)) return { ok: false, reason: "이메일 형식을 확인하세요." };
  if (typeof password !== "string" || password.trim() === "" || password.length > 256) {
    return { ok: false, reason: "비밀번호를 입력하세요 (256자 이내)." };
  }
  return { ok: true, value: { email: normalizedEmail, password } };
}

export function validateSignup({ email, username, displayName, password }) {
  const credentials = validateCredentials({ email, password });
  if (!credentials.ok) return credentials;
  const normalizedUsername = text(username).toLowerCase();
  if (!USERNAME.test(normalizedUsername)) {
    return { ok: false, reason: "사용자 이름은 영문 소문자·숫자·_·. 조합 3~32자이며 .으로 시작하거나 끝날 수 없습니다." };
  }
  const name = text(displayName);
  if (name === "" || name.length > 64) return { ok: false, reason: "표시 이름을 1~64자로 입력하세요." };
  return { ok: true, value: { ...credentials.value, username: normalizedUsername, displayName: name } };
}

export function validateCode(raw) {
  const value = String(raw ?? "").replace(/\s+/g, "");
  if (!CODE.test(value)) return { ok: false, reason: "인증 앱의 6자리 숫자 코드를 입력하세요." };
  return { ok: true, value };
}

function base64UrlToBytes(segment) {
  const base64 = segment.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (segment.length % 4)) % 4);
  const binary = atob(base64);
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

/** Display-only claim reader. Returns null for anything that is not a three-part JWT with a string sub and numeric exp. */
export function decodeClaims(token) {
  if (typeof token !== "string") return null;
  const parts = token.split(".");
  if (parts.length !== 3 || parts.some(part => part === "")) return null;
  try {
    const claims = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[1])));
    if (typeof claims.sub !== "string" || !Number.isFinite(claims.exp)) return null;
    const amr = Array.isArray(claims.amr) ? claims.amr.filter(value => FACTORS.has(value)) : [];
    if (Array.isArray(claims.amr) && amr.length !== claims.amr.length) return null;
    return { sub: claims.sub, iat: claims.iat ?? null, exp: claims.exp, amr, authTime: Number.isFinite(claims.auth_time) ? claims.auth_time : null };
  } catch {
    return null;
  }
}

/** Summarizes a token for the status line: kind, user id, remaining seconds, whether identity will accept it as fresh MFA. */
export function describeToken(token, nowSeconds) {
  if (!token) return { kind: "none", userId: null, expiresIn: 0, freshMfa: false, label: "토큰 없음" };
  const claims = decodeClaims(token);
  if (!claims) return { kind: "invalid", userId: null, expiresIn: 0, freshMfa: false, label: "해석할 수 없는 토큰" };
  const kind = claims.amr.includes("passkey") ? "passkey" : claims.amr.includes("totp") ? "totp" : "access";
  const expiresIn = Math.max(0, Math.floor(claims.exp - nowSeconds));
  const freshMfa = kind !== "access" && claims.authTime !== null && nowSeconds < claims.authTime + FRESH_MFA_SECONDS && expiresIn > 0;
  const names = { access: "일반 access (로그인)", totp: "MFA access (TOTP)", passkey: "MFA access (패스키)" };
  const label = expiresIn === 0 ? `${names[kind]} · 만료됨` : `${names[kind]} · ${expiresIn}초 남음${freshMfa ? " · 최근 MFA" : ""}`;
  return { kind, userId: claims.sub, expiresIn, freshMfa, label };
}

export function otpauthUri({ issuer = TOTP_ISSUER, account, secret }) {
  if (!/^[A-Z2-7]+$/.test(String(secret ?? ""))) throw new Error("secret must be Base32");
  const label = `${encodeURIComponent(issuer)}:${encodeURIComponent(String(account))}`;
  return `otpauth://totp/${label}?secret=${secret}&issuer=${encodeURIComponent(issuer)}&algorithm=SHA1&digits=6&period=30`;
}

const NETWORK = "로그인 서버 주소·기동 상태와 브라우저 콘솔의 CSP/CORS 차단 여부를 확인하세요.";
const BY_STEP = {
  signup: { 400: "입력 형식을 확인하세요.", 409: "이미 있는 이메일입니다. 로그인하세요.", 429: "회원가입 요청이 너무 잦습니다. 1분 뒤 다시 시도하세요." },
  login: { 400: "입력 형식을 확인하세요.", 401: "이메일 또는 비밀번호가 맞지 않습니다.", 423: "로그인이 잠시 잠겼습니다. 15분 뒤 다시 시도하세요.", 429: "로그인 시도가 너무 잦습니다(분당 2회). 1분 뒤 다시 시도하세요." },
  enroll: { 401: "로그인 토큰이 만료되었거나 유효하지 않습니다. 다시 로그인하세요.", 409: "이미 TOTP 등록이 있거나 진행 중입니다. 인증 앱 코드로 추가 인증을 진행하세요. 재시작 API는 없습니다." },
  confirm: { 400: "코드 형식을 확인하세요.", 401: "코드가 맞지 않거나 등록 확인 5분이 지났습니다. 다음 코드로 다시 시도하세요(5회 실패 시 잠김)." },
  challenge: { 401: "로그인 토큰이 만료되었거나 유효하지 않습니다.", 409: "활성 TOTP가 없거나 30초 이내에 다시 요청했거나 5회 실패로 잠겼습니다." },
  complete: { 400: "코드 형식을 확인하세요.", 401: "코드 불일치, 이미 사용한 코드, 또는 3분이 지난 challenge입니다. 새 코드로 다시 시작하세요." },
  register: { 400: "패스키 응답 형식이 잘못되었습니다.", 401: "토큰·challenge 만료 또는 서명 검증 실패입니다. 다시 시작하세요.", 403: "패스키 등록에는 최근 5분 내 TOTP 또는 패스키 MFA 토큰과 정확한 Origin이 필요합니다.", 409: "활성 TOTP가 없거나 패스키가 5개에 도달했습니다.", 429: "10초 뒤 다시 시작하세요.", 503: "identity DB 준비 상태를 확인하세요." },
  authenticate: { 400: "패스키 응답 형식이 잘못되었습니다.", 401: "토큰·challenge 만료, 다른 사용자 또는 서명 검증 실패입니다.", 403: "Origin이 정확하지 않거나 loopback 접속이 아닙니다.", 409: "등록된 패스키가 없거나 활성 TOTP가 없습니다.", 429: "10초 뒤 다시 시작하세요.", 503: "identity DB 준비 상태를 확인하세요." },
  profile: { 401: "토큰이 없거나 만료되었거나 이 서버가 받아들이지 않습니다." },
  credentials: { 401: "로그인 토큰이 만료되었거나 유효하지 않습니다.", 403: "loopback(127.0.0.1) 접속이 아닙니다.", 503: "identity DB 준비 상태를 확인하세요." },
  delete: { 400: "패스키 id 형식이 잘못되었습니다. 목록을 새로고침하세요.", 401: "토큰이 만료되었거나 유효하지 않습니다.", 403: "패스키 삭제에는 최근 5분 내 TOTP 또는 패스키 MFA 토큰과 정확한 Origin이 필요합니다.", 404: "이 계정의 패스키가 아니거나 이미 삭제되었습니다. 목록을 새로고침하세요.", 409: "TOTP가 잠긴 상태에서는 마지막 패스키를 삭제할 수 없습니다. 먼저 TOTP 잠금을 해제하세요.", 503: "identity DB 준비 상태를 확인하세요." },
  unlock: { 401: "토큰이 만료되었거나 유효하지 않습니다.", 403: "최근 5분 내 패스키 인증과 정확한 Origin 및 loopback 접속이 필요합니다.", 409: "TOTP 계정을 찾을 수 없습니다.", 503: "identity DB 준비 상태를 확인하세요." },
  reset: { 401: "토큰이 만료되었거나 유효하지 않습니다.", 403: "최근 5분 내 패스키 인증과 정확한 Origin 및 loopback 접속이 필요합니다.", 409: "활성 TOTP 계정이 없습니다.", 503: "identity DB 준비 상태를 확인하세요." },
};

/** Display-only summary of one passkey list entry; never includes key material because the server never sends it. */
export function describeCredential(entry) {
  const id = typeof entry?.id === "string" ? entry.id : "";
  const label = text(entry?.label).slice(0, 64) || "(이름 없음)";
  const when = value => (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) ? `${value.slice(0, 16).replace("T", " ")} UTC` : null;
  const created = when(entry?.createdAt) ?? "시각 알 수 없음";
  const used = when(entry?.lastUsedAt) ?? "사용 기록 없음";
  return { id, label, summary: `${label} · id ${id.slice(0, 8)}… · 등록 ${created} · 마지막 사용 ${used}` };
}

export function failureMessage(status, step) {
  if (!Number.isInteger(status) || status === 0) return NETWORK;
  const message = BY_STEP[step]?.[status];
  return `HTTP ${status}: ${message ?? "요청이 거부되었습니다. 단계 순서와 토큰 상태를 확인하세요."}`;
}

/** A token summary that can still be sent somewhere. */
export function isLive(info) {
  return Boolean(info) && info.kind !== "none" && info.kind !== "invalid" && info.expiresIn > 0;
}

/**
 * Which buttons make sense given the login-server access token and the latest identity MFA token; the servers still
 * enforce every rule. Registration needs a fresh MFA token; everything else needs the login access token.
 */
export function allowedActions({ access, mfa } = {}) {
  if (!isLive(access)) return new Set(["signup", "login"]);
  const actions = new Set(["enroll", "confirm", "challenge", "authenticate", "profile", "clear", "credentials"]);
  if (isLive(mfa) && mfa.freshMfa) {
    actions.add("register");
    actions.add("deleteCredential");
    if (mfa.kind === "passkey") {
      actions.add("unlock");
      actions.add("reset");
    }
  }
  return actions;
}
