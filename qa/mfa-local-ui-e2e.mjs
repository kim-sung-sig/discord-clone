#!/usr/bin/env node
// Opt-in local browser end-to-end check for the identity MFA page:
//   real boot legacy-auth signup/login -> TOTP enroll/confirm/step-up -> passkey register/step-up -> same-user protected API.
// Starts boot (`legacy-auth,test`, in-memory accounts) and identity (H2) on free loopback ports with the repository test key
// fixture, drives the real page in Chrome with a CDP virtual authenticator, checks negative paths and memory-only tokens,
// then stops both process trees. Nothing here is a CI default; run it by hand:
//   MFA_E2E_PLAYWRIGHT_ROOT=<checkout with node_modules/playwright> node qa/mfa-local-ui-e2e.mjs
// Env: MFA_E2E_PLAYWRIGHT_ROOT (optional), MFA_E2E_BROWSER_CHANNEL (default chrome; "chromium" uses the bundled build),
//      MFA_E2E_STARTUP_TIMEOUT_MS (default 240000). Tokens, passwords, TOTP secrets and codes are never printed or saved.
import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import crypto from "node:crypto";
import { createWriteStream, mkdirSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { createServer } from "node:net";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = fileURLToPath(new URL("..", import.meta.url));
const isWindows = process.platform === "win32";
const startupTimeoutMs = Number(process.env.MFA_E2E_STARTUP_TIMEOUT_MS ?? 240_000);
const stamp = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
const artifactDir = path.join(repoRoot, "qa", "artifacts", "mfa-local-ui-e2e", stamp);
mkdirSync(artifactDir, { recursive: true });
const log = message => console.log(`[mfa-local-ui-e2e] ${message}`);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const posix = value => value.replace(/\\/g, "/");

function fixture(name) {
  return `file:${posix(path.join(repoRoot, "backend", "modules", "identity", "src", "test", "resources", "identity", name))}`;
}

const jwt = {
  issuer: "discord-identity", audience: "discord-api", "key-id": "identity-2026-07",
  "private-key-location": fixture("ed25519-private.pem"),
  "public-key-locations": { "identity-2026-07": fixture("ed25519-public.pem") },
};

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.on("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      server.close(() => resolve(port));
    });
  });
}

function startGradle({ name, initScript, task, env }) {
  const logPath = path.join(artifactDir, `${name}.log`);
  const output = createWriteStream(logPath);
  const args = ["--no-daemon", "--console=plain", "--init-script", posix(path.join(repoRoot, "qa", "mfa-local-ui", initScript)), task];
  // Windows needs cmd.exe for the .bat wrapper; quote each argument ourselves instead of using shell: true (DEP0190).
  const quote = value => (/[\s"]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value);
  const child = isWindows
    ? spawn("cmd.exe", ["/d", "/s", "/c", `"${[quote(path.join(repoRoot, "gradlew.bat")), ...args.map(quote)].join(" ")}"`],
      { cwd: repoRoot, env: { ...process.env, ...env }, windowsVerbatimArguments: true, stdio: ["ignore", "pipe", "pipe"] })
    : spawn(path.join(repoRoot, "gradlew"), args, { cwd: repoRoot, env: { ...process.env, ...env }, detached: true, stdio: ["ignore", "pipe", "pipe"] });
  child.stdout.pipe(output);
  child.stderr.pipe(output);
  const exited = new Promise(resolve => child.on("exit", code => resolve(code)));
  log(`${name}: started (pid ${child.pid}); log ${path.relative(repoRoot, logPath)}`);
  return { name, child, exited, logPath };
}

async function waitForHealth(service, baseUrl) {
  const deadline = Date.now() + startupTimeoutMs;
  let done = false;
  service.exited.then(() => { done = true; });
  while (Date.now() < deadline) {
    if (done) throw new Error(`${service.name} exited before becoming healthy; see ${service.logPath}`);
    try {
      const response = await fetch(`${baseUrl}/actuator/health`);
      if (response.status === 200) { log(`${service.name}: healthy at ${baseUrl}`); return; }
    } catch { /* not listening yet */ }
    await sleep(1000);
  }
  throw new Error(`${service.name} did not become healthy within ${startupTimeoutMs} ms; see ${service.logPath}`);
}

function stopTree(service) {
  if (!service || service.child.exitCode !== null) return;
  if (isWindows) spawnSync("taskkill", ["/F", "/T", "/PID", String(service.child.pid)], { stdio: "ignore" });
  else { try { process.kill(-service.child.pid, "SIGTERM"); } catch { service.child.kill("SIGTERM"); } }
}

async function portClosed(port) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    try { await fetch(`http://127.0.0.1:${port}/actuator/health`); } catch { return true; }
    await sleep(1000);
  }
  return false;
}

function loadPlaywright() {
  const require = createRequire(import.meta.url);
  const roots = [process.env.MFA_E2E_PLAYWRIGHT_ROOT, repoRoot, path.join(repoRoot, "apps", "web")].filter(Boolean);
  for (const root of roots) {
    for (const name of ["playwright", "@playwright/test"]) {
      try { return require(require.resolve(name, { paths: [root] })); } catch { /* try next */ }
    }
  }
  throw new Error("Playwright not found. Run `npm ci` at the repository root or set MFA_E2E_PLAYWRIGHT_ROOT to a checkout whose node_modules contains playwright.");
}

// Runner-side authenticator app: RFC 6238 (HMAC-SHA1, 30 s, 6 digits) from the Base32 secret the page displays.
function totp(secret, atMs = Date.now()) {
  let bits = "";
  for (const character of secret) bits += "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character).toString(2).padStart(5, "0");
  const key = Buffer.from(bits.match(/.{8}/g).map(byte => parseInt(byte, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(atMs / 30_000)));
  const digest = crypto.createHmac("sha1", key).update(counter).digest();
  const offset = digest[19] & 15;
  return String((digest.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, "0");
}

const claimsOf = token => JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"));

async function main() {
  const { chromium } = loadPlaywright();
  const bootPort = await freePort();
  const identityPort = await freePort();
  const bootBase = `http://127.0.0.1:${bootPort}`;
  const identityBase = `http://localhost:${identityPort}`;
  const schema = posix(path.join(repoRoot, "backend", "services", "identity", "src", "main", "resources", "db", "mfa-schema.sql"));
  const summary = { bootPort, identityPort, steps: {}, artifactDir: path.relative(repoRoot, artifactDir) };
  let boot;
  let identity;
  let browser;
  // Ctrl+C or a terminating signal must not orphan the two JVM trees.
  for (const signal of ["SIGINT", "SIGTERM", "SIGBREAK"]) {
    process.once(signal, () => { stopTree(identity); stopTree(boot); process.exit(130); });
  }
  try {
    boot = startGradle({
      name: "boot", initScript: "boot-legacy-auth.init.gradle", task: ":backend:boot:bootRun",
      env: {
        SPRING_PROFILES_ACTIVE: "legacy-auth,test",
        SPRING_APPLICATION_JSON: JSON.stringify({
          server: { address: "127.0.0.1", port: bootPort },
          management: { health: { redis: { enabled: false } } },
          spring: { main: { "banner-mode": "off" } },
          discord: { auth: { jwt } },
        }),
      },
    });
    await waitForHealth(boot, bootBase);
    identity = startGradle({
      name: "identity", initScript: "identity-h2.init.gradle", task: ":backend:services:identity:bootRun",
      env: {
        SPRING_APPLICATION_JSON: JSON.stringify({
          server: { address: "127.0.0.1", port: identityPort, servlet: { session: { timeout: "3m", cookie: { "http-only": true, "same-site": "strict" } } } },
          spring: { main: { "banner-mode": "off" } },
          discord: { auth: {
            jwt,
            mfa: {
              enabled: true, "database-user": "sa", "database-password": "",
              "jdbc-url": `jdbc:h2:mem:mfa-local-ui;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '${schema}'`,
              "encryption-key": crypto.randomBytes(32).toString("base64"),
            },
            webauthn: { enabled: true, origin: identityBase },
          } },
        }),
      },
    });
    await waitForHealth(identity, `http://127.0.0.1:${identityPort}`);

    const channel = process.env.MFA_E2E_BROWSER_CHANNEL ?? "chrome";
    browser = await chromium.launch(channel === "chromium" ? { headless: true } : { channel, headless: true });
    const context = await browser.newContext();
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await cdp.send("WebAuthn.enable");
    await cdp.send("WebAuthn.addVirtualAuthenticator", { options: {
      protocol: "ctap2", transport: "internal", hasResidentKey: true, hasUserVerification: true, isUserVerified: true, automaticPresenceSimulation: true,
    } });
    const statusText = () => page.locator("#status").innerText();
    const waitStatus = (prefix, timeout = 20_000) => page.waitForFunction(
      expected => document.getElementById("status").textContent.startsWith(expected), prefix, { timeout });
    const clickAndCapture = async (button, urlSuffix) => {
      const waiting = page.waitForResponse(response => response.url().endsWith(urlSuffix), { timeout: 20_000 });
      await page.locator(button).click();
      return waiting;
    };

    await page.goto(`${identityBase}/mfa-webauthn.html`);
    await waitStatus("준비되었습니다");
    for (const id of ["enroll", "confirm", "step-up-totp", "register", "authenticate", "profile", "credentials"]) {
      assert.equal(await page.locator(`#${id}`).isDisabled(), true, `${id} disabled before login`);
    }

    // 1. Real legacy-auth account: run-unique e-mail, random password kept only in this process.
    const email = `mfa-local-${stamp}@example.com`;
    const password = crypto.randomBytes(18).toString("base64url");
    await page.locator("#login-base").fill(bootBase);
    await page.locator("#email").fill(email);
    await page.locator("#username").fill(`mfalocal${stamp.slice(-8)}`);
    await page.locator("#display-name").fill("MFA Local");
    await page.locator("#password").fill(password);
    const signup = await clickAndCapture("#signup", "/api/auth/signup");
    summary.steps.signup = signup.status();
    assert.equal(signup.status(), 201);
    await waitStatus("가입 완료");
    // Length-only assertions: a failure message must never echo the password.
    assert.equal((await page.locator("#password").inputValue()).length, 0, "password field cleared after signup");

    await page.locator("#password").fill(`${password}-wrong`);
    const badLogin = await clickAndCapture("#login", "/api/auth/login");
    summary.steps.loginWrongPassword = badLogin.status();
    assert.equal(badLogin.status(), 401);
    await waitStatus("HTTP 401");
    assert.match(await statusText(), /이메일 또는 비밀번호/);
    assert.match(await page.locator("#token-state").innerText(), /토큰 없음/);

    await page.locator("#password").fill(password);
    const login = await clickAndCapture("#login", "/api/auth/login");
    summary.steps.login = login.status();
    assert.equal(login.status(), 200);
    const loginBody = await login.json();
    const userId = loginBody.user.id;
    const loginToken = loginBody.accessToken;
    summary.userId = userId;
    assert.equal(claimsOf(loginToken).sub, userId);
    assert.equal(claimsOf(loginToken).amr, undefined, "login token carries no MFA evidence");
    await waitStatus("로그인 완료");
    assert.equal((await page.locator("#password").inputValue()).length, 0, "password field cleared after login");
    assert.match(await page.locator("#token-state").innerText(), new RegExp(`일반 access.*${userId}`));

    // 2. TOTP: enroll, one wrong code (401), confirm, then a fresh step for the challenge.
    const enroll = await clickAndCapture("#enroll", "/api/auth/mfa/totp/enroll");
    summary.steps.enroll = enroll.status();
    assert.equal(enroll.status(), 200);
    await waitStatus("TOTP 등록 시작");
    const secret = (await page.locator("#secret").innerText()).trim();
    assert.ok(/^[A-Z2-7]{32}$/.test(secret), "displayed secret is 32-char Base32");
    assert.ok((await page.locator("#otpauth").innerText()).startsWith("otpauth://totp/"));
    // A code that matches none of the accepted steps (current-1, current, current+1).
    const accepted = new Set([-30_000, 0, 30_000].map(delta => totp(secret, Date.now() + delta)));
    let wrong = "000000";
    while (accepted.has(wrong)) wrong = String((Number(wrong) + 1) % 1_000_000).padStart(6, "0");
    await page.locator("#code").fill(wrong);
    const badConfirm = await clickAndCapture("#confirm", "/api/auth/mfa/totp/confirm");
    summary.steps.confirmWrongCode = badConfirm.status();
    assert.equal(badConfirm.status(), 401);
    await waitStatus("HTTP 401");
    assert.match(await statusText(), /코드가 맞지 않/);
    if (Date.now() % 30_000 > 25_000) await sleep(30_000 - (Date.now() % 30_000) + 200);
    await page.locator("#code").fill(totp(secret));
    const confirm = await clickAndCapture("#confirm", "/api/auth/mfa/totp/confirm");
    summary.steps.confirm = confirm.status();
    assert.equal(confirm.status(), 204);
    await waitStatus("TOTP 등록 확인 완료");
    assert.equal(await page.locator("#secret-panel").isHidden(), true, "secret removed after confirmation");

    // Verification accepts current-1..current+1 with step > consumed_step, so the next step's code works immediately.
    await page.locator("#code").fill(totp(secret, Date.now() + 30_000));
    const complete = await clickAndCapture("#step-up-totp", "/complete");
    summary.steps.totpComplete = complete.status();
    assert.equal(complete.status(), 200);
    const totpToken = (await complete.json()).accessToken;
    assert.deepEqual(claimsOf(totpToken).amr, ["totp"]);
    assert.equal(claimsOf(totpToken).sub, userId);
    await waitStatus("TOTP 추가 인증 완료");
    assert.match(await page.locator("#token-state").innerText(), /일반 access.*MFA access: MFA access \(TOTP\)/);

    // Negative: the plain login token (and no token) must not open passkey registration; the browser sets the real Origin.
    const direct = await page.evaluate(async token => {
      const headers = token ? { Authorization: `Bearer ${token}` } : {};
      const withToken = await fetch("/api/auth/mfa/webauthn/register/options", { method: "POST", headers, credentials: "same-origin" });
      const withoutToken = await fetch("/api/auth/mfa/webauthn/register/options", { method: "POST", credentials: "same-origin" });
      return [withToken.status, withoutToken.status];
    }, loginToken);
    summary.steps.registerOptionsWithLoginToken = direct[0];
    summary.steps.registerOptionsWithoutToken = direct[1];
    assert.deepEqual(direct, [403, 401]);

    // 3. Passkey: register with the fresh TOTP token, wait out the 10 s throttle, then step up.
    const register = await clickAndCapture("#register", "/register/complete");
    summary.steps.registerComplete = register.status();
    assert.equal(register.status(), 204);
    await waitStatus("패스키 등록 완료 (현재 1개)");
    // 3-1. Account-bound list with the plain login token: one entry, no key material in the payload.
    const listed = await clickAndCapture("#credentials", "/webauthn/credentials");
    summary.steps.credentialsList = listed.status();
    assert.equal(listed.status(), 200);
    const credentialsBody = await listed.json();
    assert.equal(credentialsBody.length, 1);
    assert.deepEqual(Object.keys(credentialsBody[0]).sort(), ["createdAt", "id", "label", "lastUsedAt"]);
    const passkeyId = credentialsBody[0].id;
    assert.equal(await page.locator("#credential-list li").count(), 1);
    await waitStatus("이 계정의 패스키 1개");
    await sleep(10_100);
    const authenticate = await clickAndCapture("#authenticate", "/authenticate/complete");
    summary.steps.authenticateComplete = authenticate.status();
    assert.equal(authenticate.status(), 200);
    const passkeyToken = (await authenticate.json()).accessToken;
    assert.deepEqual(claimsOf(passkeyToken).amr, ["passkey"]);
    assert.equal(claimsOf(passkeyToken).sub, userId);
    assert.ok(claimsOf(passkeyToken).exp - claimsOf(passkeyToken).auth_time <= 300);
    await waitStatus("패스키 인증 완료");
    assert.match(await page.locator("#token-state").innerText(), /MFA access \(패스키\)/);

    // 4. Same user at both protected APIs (identity and the login server) with the passkey token.
    const bootProfile = page.waitForResponse(response => response.url() === `${bootBase}/api/users/@me`);
    const identityProfile = await clickAndCapture("#profile", `${identityBase}/api/users/@me`);
    summary.steps.identityProfile = identityProfile.status();
    summary.steps.loginServerProfile = (await bootProfile).status();
    await waitStatus("보호 API 확인 성공 [MFA access (패스키)]");
    const success = await statusText();
    assert.equal(success.split(userId).length - 1, 3, "identity, login server and login response agree on the user");

    // 5. Five failed TOTP attempts lock the factor; only a fresh passkey token can unlock/reset it.
    await sleep(30_100); // the successful TOTP step-up also starts the per-user challenge throttle
    for (let attempt = 0; attempt < 5; attempt++) {
      const candidateCodes = new Set([-30_000, 0, 30_000].map(delta => totp(secret, Date.now() + delta)));
      let wrongCode = "000000";
      while (candidateCodes.has(wrongCode)) wrongCode = String((Number(wrongCode) + 1) % 1_000_000).padStart(6, "0");
      const result = await page.evaluate(async ({ token, code }) => {
        const headers = { Authorization: `Bearer ${token}` };
        const challengeResponse = await fetch("/api/auth/mfa/totp/challenges", { method: "POST", headers });
        if (!challengeResponse.ok) return { challenge: challengeResponse.status, complete: null };
        const { challengeId } = await challengeResponse.json();
        const completeResponse = await fetch(`/api/auth/mfa/totp/challenges/${challengeId}/complete`, {
          method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: JSON.stringify({ code }),
        });
        return { challenge: challengeResponse.status, complete: completeResponse.status };
      }, { token: loginToken, code: wrongCode });
      assert.deepEqual(result, { challenge: 200, complete: 401 });
      if (attempt < 4) await sleep(30_100); // per-user TOTP challenge throttle
    }
    const locked = await page.evaluate(async token => (await fetch("/api/auth/mfa/totp/challenges", {
      method: "POST", headers: { Authorization: `Bearer ${token}` },
    })).status, loginToken);
    summary.steps.lockedChallenge = locked;
    assert.equal(locked, 409);

    await sleep(10_100);
    const reauthenticate = await clickAndCapture("#authenticate", "/authenticate/complete");
    assert.equal(reauthenticate.status(), 200);
    assert.deepEqual(claimsOf((await reauthenticate.json()).accessToken).amr, ["passkey"]);
    const unlock = await clickAndCapture("#unlock-totp", "/api/auth/mfa/totp/unlock");
    summary.steps.unlock = unlock.status();
    assert.equal(unlock.status(), 204);
    const reset = await clickAndCapture("#reset-totp", "/api/auth/mfa/totp/reset");
    summary.steps.reset = reset.status();
    assert.equal(reset.status(), 200);
    await waitStatus("새 TOTP secret은 5분 동안 대기합니다");
    const newSecret = (await page.locator("#secret").innerText()).trim();
    await page.locator("#code").fill(totp(newSecret));
    const confirmNew = await clickAndCapture("#confirm", "/api/auth/mfa/totp/confirm");
    summary.steps.confirmNewSecret = confirmNew.status();
    assert.equal(confirmNew.status(), 204);
    await waitStatus("TOTP 등록 확인 완료");
    const oldCode = totp(secret, Date.now() + 30_000);
    const oldCodeResult = await page.evaluate(async ({ token, code }) => {
      const headers = { Authorization: `Bearer ${token}` };
      const challenge = await fetch("/api/auth/mfa/totp/challenges", { method: "POST", headers });
      if (!challenge.ok) return { challenge: challenge.status, complete: null };
      const { challengeId } = await challenge.json();
      const complete = await fetch(`/api/auth/mfa/totp/challenges/${challengeId}/complete`, {
        method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: JSON.stringify({ code }),
      });
      return { challenge: challenge.status, complete: complete.status };
    }, { token: loginToken, code: oldCode });
    assert.deepEqual(oldCodeResult, { challenge: 200, complete: 401 });
    summary.steps.oldSecretCode = oldCodeResult.complete;
    await sleep(30_100);
    await page.locator("#code").fill(totp(newSecret, Date.now() + 30_000));
    const newStepUp = await clickAndCapture("#step-up-totp", "/complete");
    assert.equal(newStepUp.status(), 200);
    await waitStatus("TOTP 추가 인증 완료");
    const deleteLast = page.waitForResponse(response => response.url().endsWith(`/webauthn/credentials/${passkeyId}`), { timeout: 20_000 });
    await page.locator(`#credential-list button[data-id="${passkeyId}"]`).click();
    const deleted = await deleteLast;
    summary.steps.deleteCredential = deleted.status();
    assert.equal(deleted.status(), 204);
    await waitStatus("패스키를 삭제했습니다. 남은 패스키 0개");
    const emptyList = await clickAndCapture("#credentials", "/webauthn/credentials");
    summary.steps.listAfterDelete = emptyList.status();
    assert.equal(emptyList.status(), 200);
    assert.deepEqual(await emptyList.json(), []);

    // Memory-only: no web storage, no IndexedDB, no refresh cookie; only the identity ceremony cookie may exist.
    summary.storageEntries = await page.evaluate(async () => localStorage.length + sessionStorage.length + (await indexedDB.databases()).length);
    assert.equal(summary.storageEntries, 0);
    summary.cookieNames = (await context.cookies()).map(cookie => cookie.name);
    assert.ok(!summary.cookieNames.includes("dc_refresh"), "login server refresh cookie must not be stored");
    assert.ok(summary.cookieNames.every(name => name === "JSESSIONID"), `unexpected cookies: ${summary.cookieNames}`);

    await page.locator("#clear").click();
    await waitStatus("이 페이지의 토큰을 지웠습니다");
    assert.match(await page.locator("#token-state").innerText(), /토큰 없음/);
    assert.equal(await page.locator("#profile").isDisabled(), true);
    assert.equal((await page.locator("#password").inputValue()).length, 0, "password field empty after clear");
    assert.equal(await page.locator("#secret-panel").isHidden(), true);
    summary.steps.profileWithoutToken = await page.evaluate(async () => (await fetch("/api/users/@me")).status);
    assert.equal(summary.steps.profileWithoutToken, 401);

    writeFileSync(path.join(artifactDir, "summary.json"), JSON.stringify(summary, null, 2));
    log(`summary ${JSON.stringify(summary)}`);
    console.log("PASS: real legacy-auth login -> TOTP -> passkey -> same-user protected API (memory-only tokens, virtual authenticator, H2)");
  } finally {
    if (browser) await browser.close().catch(() => {});
    stopTree(identity);
    stopTree(boot);
    const closed = [await portClosed(identityPort), await portClosed(bootPort)];
    log(`cleanup: identity port closed=${closed[0]} boot port closed=${closed[1]}`);
    if (!closed.every(Boolean)) process.exitCode = 3;
  }
}

main().catch(error => {
  console.error(`FAIL: ${error.message}`);
  process.exitCode = process.exitCode || 1;
});
