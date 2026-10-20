// DOM wiring for the local MFA page. Tokens, passwords, codes and the TOTP secret live only in this module's variables
// and the visible inputs; nothing is written to storage, cookies (by this script), URLs or the document title.
// Two tokens are kept apart: the login server's one-hour access token and identity's latest five-minute MFA token.
import {
  allowedActions, describeCredential, describeToken, failureMessage, isLive, otpauthUri,
  validateCode, validateCredentials, validateLoginBaseUrl, validateSignup,
} from "./mfa-local-core.js";

let accessToken = "";
let mfaToken = "";
let loginUserId = "";

const $ = id => document.getElementById(id);
const status = $("status");
// Re-queried on every render because the passkey list adds and removes its own delete buttons.
const buttons = () => [...document.querySelectorAll("button[data-action]")];
const nowSeconds = () => Math.floor(Date.now() / 1000);
const supported = window.isSecureContext && window.PublicKeyCredential
  && typeof PublicKeyCredential.parseCreationOptionsFromJSON === "function"
  && typeof PublicKeyCredential.parseRequestOptionsFromJSON === "function"
  && typeof PublicKeyCredential.prototype.toJSON === "function";

class StepError extends Error { }

function tokens() {
  return { access: describeToken(accessToken, nowSeconds()), mfa: describeToken(mfaToken, nowSeconds()) };
}

function render() {
  const { access, mfa } = tokens();
  $("token-state").textContent = access.kind === "none"
    ? "토큰 없음 (로그인 필요)"
    : `일반 access: ${access.label} · 사용자 ${access.userId} | MFA access: ${mfa.kind === "none" ? "없음" : mfa.label}`;
  const allowed = allowedActions({ access, mfa });
  buttons().forEach(button => { button.disabled = !allowed.has(button.dataset.action); });
}

function renderCredentials(list) {
  const container = $("credential-list");
  container.replaceChildren();
  for (const entry of list) {
    const info = describeCredential(entry);
    if (!info.id) continue;
    const item = document.createElement("li");
    const text = document.createElement("span");
    text.textContent = info.summary;
    const remove = document.createElement("button");
    remove.type = "button";
    remove.dataset.action = "deleteCredential";
    remove.dataset.id = info.id;
    remove.textContent = "삭제";
    item.append(text, " ", remove);
    container.append(item);
  }
  $("credential-empty").hidden = list.length !== 0;
}

async function refreshCredentials() {
  const list = await send("/api/auth/mfa/webauthn/credentials", { step: "credentials", method: "GET" });
  if (!Array.isArray(list)) throw new StepError("패스키 목록 응답 형식이 예상과 다릅니다.");
  renderCredentials(list);
  return list;
}

function requireLoginBase() {
  const checked = validateLoginBaseUrl($("login-base").value);
  if (!checked.ok) throw new StepError(checked.reason);
  return checked.value;
}

function bearerFor(kind) {
  const token = kind === "mfa" ? mfaToken : accessToken;
  if (!token) throw new StepError(kind === "mfa" ? "먼저 TOTP 또는 패스키로 추가 인증하세요." : "먼저 로그인하세요.");
  return `Bearer ${token}`;
}

async function send(url, { step, method = "POST", body, bearer = "access", sameOrigin = true, parse = true }) {
  const headers = body === undefined ? {} : { "Content-Type": "application/json" };
  if (bearer !== "none") headers.Authorization = bearerFor(bearer);
  let response;
  try {
    response = await fetch(url, {
      method, headers, cache: "no-store",
      credentials: sameOrigin ? "same-origin" : "omit",
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch {
    throw new StepError(failureMessage(0, step));
  }
  if (!response.ok) throw new StepError(failureMessage(response.status, step));
  if (!parse || response.status === 204) return null;
  return response.json();
}

const loginServer = (path, options) => send(requireLoginBase() + path, { ...options, sameOrigin: false });

async function run(action) {
  buttons().forEach(button => { button.disabled = true; });
  status.textContent = "진행 중입니다.";
  try {
    await action();
  } catch (error) {
    if (error instanceof StepError) status.textContent = error.message;
    else if (error?.name === "NotAllowedError") status.textContent = "취소·시간 초과·사용 가능한 패스키 없음: 조건을 확인하고 다시 시작하세요.";
    else if (error instanceof DOMException) status.textContent = `브라우저 오류: ${error.name}`;
    else status.textContent = "예상하지 못한 오류입니다. 브라우저 콘솔을 확인하세요.";
  } finally {
    render();
  }
}

function credentialsFromForm(signup) {
  const input = { email: $("email").value, password: $("password").value, username: $("username").value, displayName: $("display-name").value };
  const checked = signup ? validateSignup(input) : validateCredentials(input);
  if (!checked.ok) throw new StepError(checked.reason);
  $("password").value = "";
  return checked.value;
}

function clearSecretPanel() {
  $("secret").textContent = "";
  $("otpauth").textContent = "";
  $("secret-panel").hidden = true;
  $("code").value = "";
}

function requireCode() {
  const code = validateCode($("code").value);
  $("code").value = "";
  if (!code.ok) throw new StepError(code.reason);
  return code.value;
}

$("login-form").addEventListener("submit", event => event.preventDefault());

$("signup").addEventListener("click", () => run(async () => {
  const account = credentialsFromForm(true);
  await loginServer("/api/auth/signup", { step: "signup", body: account, bearer: "none", parse: false });
  status.textContent = "가입 완료. 같은 이메일과 비밀번호로 로그인하세요. (가입 응답의 토큰은 사용하지 않았습니다.)";
}));

$("login").addEventListener("click", () => run(async () => {
  const account = credentialsFromForm(false);
  const result = await loginServer("/api/auth/login", { step: "login", body: account, bearer: "none" });
  if (typeof result?.accessToken !== "string" || typeof result?.user?.id !== "string") throw new StepError("로그인 응답 형식이 예상과 다릅니다.");
  accessToken = result.accessToken;
  mfaToken = "";
  loginUserId = result.user.id;
  clearSecretPanel();
  renderCredentials([]);
  $("credential-empty").hidden = true;
  status.textContent = `로그인 완료. 사용자 ${loginUserId}. 이제 TOTP 등록을 시작하세요.`;
}));

$("enroll").addEventListener("click", () => run(async () => {
  const result = await send("/api/auth/mfa/totp/enroll", { step: "enroll" });
  if (!/^[A-Z2-7]+$/.test(result?.secret ?? "")) throw new StepError("등록 응답 형식이 예상과 다릅니다.");
  $("secret").textContent = result.secret;
  $("otpauth").textContent = otpauthUri({ account: loginUserId, secret: result.secret });
  $("secret-panel").hidden = false;
  status.textContent = "TOTP 등록 시작. 인증 앱에 secret을 등록한 뒤 현재 코드를 입력하고 5분 안에 등록 확인을 누르세요.";
}));

$("confirm").addEventListener("click", () => run(async () => {
  const code = requireCode();
  await send("/api/auth/mfa/totp/confirm", { step: "confirm", body: { code }, parse: false });
  clearSecretPanel();
  status.textContent = "TOTP 등록 확인 완료. 확인에 쓴 코드는 재사용할 수 없습니다. 인증 앱의 다음 코드를 입력하고 TOTP로 추가 인증을 누르세요.";
}));

$("step-up-totp").addEventListener("click", () => run(async () => {
  const code = requireCode();
  const challenge = await send("/api/auth/mfa/totp/challenges", { step: "challenge" });
  if (typeof challenge?.challengeId !== "string") throw new StepError("challenge 응답 형식이 예상과 다릅니다.");
  const result = await send(`/api/auth/mfa/totp/challenges/${encodeURIComponent(challenge.challengeId)}/complete`, { step: "complete", body: { code } });
  if (typeof result?.accessToken !== "string") throw new StepError("complete 응답 형식이 예상과 다릅니다.");
  mfaToken = result.accessToken;
  clearSecretPanel();
  status.textContent = "TOTP 추가 인증 완료. 5분 MFA 토큰을 받았습니다. 이 안에 패스키 등록을 진행하세요. 로그인 토큰은 그대로 유지됩니다.";
}));

$("unlock-totp").addEventListener("click", () => run(async () => {
  await send("/api/auth/mfa/totp/unlock", { step: "unlock", bearer: "mfa", parse: false });
  status.textContent = "패스키 인증으로 TOTP 잠금을 해제했습니다. 실패 횟수만 초기화했고 기존 TOTP secret과 사용 기록은 유지됩니다.";
}));

$("reset-totp").addEventListener("click", () => run(async () => {
  const result = await send("/api/auth/mfa/totp/reset", { step: "reset", bearer: "mfa" });
  if (!/^[A-Z2-7]+$/.test(result?.secret ?? "")) throw new StepError("재설정 응답 형식이 예상과 다릅니다.");
  $("secret").textContent = result.secret;
  $("otpauth").textContent = otpauthUri({ account: loginUserId, secret: result.secret });
  $("secret-panel").hidden = false;
  status.textContent = "새 TOTP secret은 5분 동안 대기합니다. 기존 secret은 새 코드를 확인할 때까지 계속 유효합니다.";
}));

$("register").addEventListener("click", () => run(async () => {
  if (!supported) throw new StepError("WebAuthn JSON API를 지원하는 최신 브라우저와 localhost 주소가 필요합니다.");
  const label = $("label").value.trim();
  if (!label || label.length > 64) throw new StepError("패스키 이름을 1~64자로 입력하세요.");
  const options = await send("/api/auth/mfa/webauthn/register/options", { step: "register", bearer: "mfa" });
  const credential = await navigator.credentials.create({ publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(options) });
  if (!credential) throw new StepError("등록이 취소되었습니다.");
  await send("/api/auth/mfa/webauthn/register/complete", { step: "register", bearer: "mfa", body: { credential: credential.toJSON(), label }, parse: false });
  const list = await refreshCredentials();
  status.textContent = `패스키 등록 완료 (현재 ${list.length}개). 10초 뒤 패스키로 추가 인증을 누르세요. 등록 자체로 새 토큰을 발급하지는 않습니다.`;
}));

$("credentials").addEventListener("click", () => run(async () => {
  const list = await refreshCredentials();
  status.textContent = `이 계정의 패스키 ${list.length}개를 불러왔습니다.`;
}));

$("credential-list").addEventListener("click", event => {
  const remove = event.target.closest("button[data-action=\"deleteCredential\"]");
  if (!remove || remove.disabled) return;
  const id = remove.dataset.id;
  run(async () => {
    await send(`/api/auth/mfa/webauthn/credentials/${encodeURIComponent(id)}`, { step: "delete", method: "DELETE", bearer: "mfa", parse: false });
    const list = await refreshCredentials();
    status.textContent = `패스키를 삭제했습니다. 남은 패스키 ${list.length}개.`;
  });
});

$("authenticate").addEventListener("click", () => run(async () => {
  if (!supported) throw new StepError("WebAuthn JSON API를 지원하는 최신 브라우저와 localhost 주소가 필요합니다.");
  const options = await send("/api/auth/mfa/webauthn/authenticate/options", { step: "authenticate" });
  const credential = await navigator.credentials.get({ publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(options) });
  if (!credential) throw new StepError("인증이 취소되었습니다.");
  const result = await send("/api/auth/mfa/webauthn/authenticate/complete", { step: "authenticate", body: credential.toJSON() });
  if (typeof result?.accessToken !== "string") throw new StepError("인증 응답 형식이 예상과 다릅니다.");
  mfaToken = result.accessToken;
  status.textContent = "패스키 인증 완료. 5분 MFA 토큰을 받았습니다. 보호 API 확인을 눌러 같은 사용자인지 확인하세요.";
}));

$("profile").addEventListener("click", () => run(async () => {
  const { mfa } = tokens();
  const bearer = isLive(mfa) ? "mfa" : "access";
  const used = bearer === "mfa" ? mfa.label.split(" · ")[0] : "일반 access (로그인)";
  const identity = await send("/api/users/@me", { step: "profile", method: "GET", bearer });
  const server = await loginServer("/api/users/@me", { step: "profile", method: "GET", bearer });
  const ids = [identity?.id, server?.id, loginUserId];
  if (ids.some(id => typeof id !== "string" || id === "")) throw new StepError("프로필 응답에 사용자 id가 없습니다.");
  if (new Set(ids).size !== 1) throw new StepError(`사용자 불일치: identity=${ids[0]} 로그인서버=${ids[1]} 로그인사용자=${ids[2]}`);
  status.textContent = `보호 API 확인 성공 [${used}]: identity=${ids[0]} 로그인서버=${ids[1]} 로그인사용자=${ids[2]} (동일)`;
}));

$("clear").addEventListener("click", () => {
  accessToken = "";
  mfaToken = "";
  loginUserId = "";
  clearSecretPanel();
  renderCredentials([]);
  $("credential-empty").hidden = true;
  $("password").value = "";
  status.textContent = "이 페이지의 토큰을 지웠습니다. 서버 토큰 폐기나 패스키 삭제는 아닙니다.";
  render();
});

status.textContent = supported
  ? "준비되었습니다. 로그인 서버 주소를 확인하고 로그인하세요."
  : "이 브라우저는 필요한 WebAuthn JSON API를 지원하지 않거나 secure context가 아닙니다. 로그인과 TOTP는 계속 사용할 수 있습니다.";
render();
