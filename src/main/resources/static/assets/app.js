(() => {
  "use strict";

  const LS = {
    baseUrl: "grid07_baseUrl",
    userId: "grid07_userId",
    botId: "grid07_botId",
    postId: "grid07_postId",
  };

  let requestCount = 0;
  let bundledCount = 0;

  const $ = (id) => document.getElementById(id);

  const els = {
    baseUrl: $("baseUrl"),
    originPill: $("origin-pill"),
    log: $("log"),
    mRequests: $("m-requests"),
    mBundled: $("m-bundled"),
    mLastMs: $("m-last-ms"),
    userId: $("userId"),
    botId: $("botId"),
    postId: $("postId"),
    postContent: $("postContent"),
    humanComment: $("humanComment"),
    botComment: $("botComment"),
    snapshotJson: $("snapshot-json"),
    actorsJson: $("actors-json"),
    burstN: $("burstN"),
  };

  function load() {
    els.baseUrl.value = localStorage.getItem(LS.baseUrl) || "";
    els.userId.value = localStorage.getItem(LS.userId) || "";
    els.botId.value = localStorage.getItem(LS.botId) || "";
    els.postId.value = localStorage.getItem(LS.postId) || "";
    updateOriginPill();
  }

  function save() {
    localStorage.setItem(LS.baseUrl, els.baseUrl.value.trim());
    localStorage.setItem(LS.userId, els.userId.value.trim());
    localStorage.setItem(LS.botId, els.botId.value.trim());
    localStorage.setItem(LS.postId, els.postId.value.trim());
  }

  function apiRoot() {
    const raw = els.baseUrl.value.trim().replace(/\/+$/, "");
    return raw || "";
  }

  function updateOriginPill() {
    const r = apiRoot();
    els.originPill.textContent = r ? `API: ${r}` : "API: same origin";
    els.originPill.title = r || window.location.origin;
  }

  function url(path) {
    const r = apiRoot();
    if (!r) return path.startsWith("/") ? path : `/${path}`;
    return `${r}${path.startsWith("/") ? path : `/${path}`}`;
  }

  function logLine(msg, level = "info") {
    const line = document.createElement("div");
    line.className = `log-line ${level === "error" ? "err" : level === "warn" ? "warn" : level === "ok" ? "ok" : ""}`;
    const t = new Date().toISOString().slice(11, 23);
    line.textContent = `[${t}] ${msg}`;
    els.log.prepend(line);
  }

  function bumpRequests(n = 1) {
    requestCount += n;
    els.mRequests.textContent = String(requestCount);
  }

  async function fetchJson(path, options = {}) {
    const res = await fetch(url(path), {
      ...options,
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...(options.headers || {}),
      },
    });
    bumpRequests(1);
    let bodyText = await res.text();
    let json = null;
    try {
      json = bodyText ? JSON.parse(bodyText) : null;
    } catch {
      json = { _raw: bodyText };
    }
    return { res, json };
  }

  function randomSuffix() {
    try {
      return crypto.randomUUID().slice(0, 8);
    } catch {
      return String(Math.floor(Math.random() * 1e9));
    }
  }

  async function handlePing() {
    save();
    try {
      const { res, json } = await fetchJson("/api/users");
      if (res.ok) {
        logLine(`GET /api/users → ${res.status}`, "ok");
      } else {
        logLine(`GET /api/users → ${res.status} ${JSON.stringify(json)}`, "warn");
      }
    } catch (e) {
      logLine(`Ping failed: ${e instanceof Error ? e.message : String(e)}`, "error");
    }
  }

  async function handleCreateUser() {
    save();
    const suffix = randomSuffix();
    const username = `demo_user_${suffix}`;
    const { res, json } = await fetchJson("/api/users", {
      method: "POST",
      body: JSON.stringify({ username, premium: true }),
    });
    if (res.ok && json && json.data && json.data.id != null) {
      els.userId.value = String(json.data.id);
      save();
      logLine(`User created: ${username} (id ${json.data.id})`, "ok");
    } else {
      logLine(`Create user failed: ${res.status} ${JSON.stringify(json)}`, "error");
    }
    els.actorsJson.hidden = false;
    els.actorsJson.textContent = JSON.stringify(json, null, 2);
  }

  async function handleCreateBot() {
    save();
    const suffix = randomSuffix();
    const name = `DemoBot_${suffix}`;
    const { res, json } = await fetchJson("/api/bots", {
      method: "POST",
      body: JSON.stringify({
        name,
        personaDescription: "Console-generated bot for Grid07 demos.",
      }),
    });
    if (res.ok && json && json.data && json.data.id != null) {
      els.botId.value = String(json.data.id);
      save();
      logLine(`Bot created: ${name} (id ${json.data.id})`, "ok");
    } else {
      logLine(`Create bot failed: ${res.status} ${JSON.stringify(json)}`, "error");
    }
    els.actorsJson.hidden = false;
    els.actorsJson.textContent = JSON.stringify(json, null, 2);
  }

  function requireIds() {
    const userId = els.userId.value.trim();
    const botId = els.botId.value.trim();
    const postId = els.postId.value.trim();
    return { userId, botId, postId };
  }

  async function handleCreatePost() {
    save();
    const { userId } = requireIds();
    if (!userId) {
      logLine("Set or create a user ID first.", "warn");
      return;
    }
    const content =
      els.postContent.value.trim() ||
      `Grid07 console smoke test — ${new Date().toISOString()}`;
    const { res, json } = await fetchJson("/api/posts", {
      method: "POST",
      body: JSON.stringify({
        authorId: Number(userId),
        authorType: "USER",
        content,
      }),
    });
    if (res.status === 201 && json && json.data && json.data.id != null) {
      els.postId.value = String(json.data.id);
      save();
      logLine(`Post created id ${json.data.id}`, "ok");
    } else {
      logLine(`Create post failed: ${res.status} ${JSON.stringify(json)}`, "error");
    }
    await handleBundleRefresh();
  }

  async function handleBundleRefresh() {
    save();
    const postId = els.postId.value.trim();
    if (!postId) {
      logLine("Set a post ID first.", "warn");
      return;
    }
    const t0 = performance.now();
    try {
      const [p, c, s] = await Promise.all([
        fetchJson(`/api/posts/${encodeURIComponent(postId)}`),
        fetchJson(`/api/posts/${encodeURIComponent(postId)}/comments`),
        fetchJson(`/api/debug/posts/${encodeURIComponent(postId)}/stats`),
      ]);
      bundledCount += 1;
      els.mBundled.textContent = String(bundledCount);
      els.mLastMs.textContent = String(Math.round(performance.now() - t0));

      const bundle = {
        post: p.json,
        comments: c.json,
        redis: s.json,
        statuses: {
          post: p.res.status,
          comments: c.res.status,
          redis: s.res.status,
        },
      };
      els.snapshotJson.textContent = JSON.stringify(bundle, null, 2);
      logLine(`Bundle refresh for post ${postId} (${Math.round(performance.now() - t0)}ms)`, "ok");
    } catch (e) {
      logLine(`Bundle refresh failed: ${e instanceof Error ? e.message : String(e)}`, "error");
    }
  }

  async function handleHumanComment() {
    save();
    const { userId, postId } = requireIds();
    if (!userId || !postId) {
      logLine("Need user ID and post ID.", "warn");
      return;
    }
    const content = els.humanComment.value.trim() || "Great post from the console.";
    const { res, json } = await fetchJson(`/api/posts/${encodeURIComponent(postId)}/comments`, {
      method: "POST",
      body: JSON.stringify({
        authorId: Number(userId),
        authorType: "USER",
        content,
        depthLevel: 0,
      }),
    });
    logLine(`Human comment → ${res.status}`, res.ok ? "ok" : "warn");
    if (!res.ok) logLine(JSON.stringify(json), "error");
    await handleBundleRefresh();
  }

  async function handleBotComment() {
    save();
    const { botId, postId } = requireIds();
    if (!botId || !postId) {
      logLine("Need bot ID and post ID.", "warn");
      return;
    }
    const content = els.botComment.value.trim() || "Automated bot note from the console.";
    const { res, json } = await fetchJson(`/api/posts/${encodeURIComponent(postId)}/comments`, {
      method: "POST",
      body: JSON.stringify({
        authorId: Number(botId),
        authorType: "BOT",
        content,
        depthLevel: 0,
      }),
    });
    logLine(`Bot comment → ${res.status}`, res.ok ? "ok" : res.status === 429 ? "warn" : "error");
    if (!res.ok) logLine(JSON.stringify(json), res.status === 429 ? "warn" : "error");
    await handleBundleRefresh();
  }

  async function handleLike() {
    save();
    const { userId, postId } = requireIds();
    if (!userId || !postId) {
      logLine("Need user ID and post ID.", "warn");
      return;
    }
    const { res, json } = await fetchJson(`/api/posts/${encodeURIComponent(postId)}/like`, {
      method: "POST",
      body: JSON.stringify({ userId: Number(userId) }),
    });
    logLine(`Like → ${res.status}`, res.ok ? "ok" : "error");
    if (!res.ok) logLine(JSON.stringify(json), "error");
    await handleBundleRefresh();
  }

  async function handleDepthBad() {
    save();
    const { userId, postId } = requireIds();
    if (!userId || !postId) {
      logLine("Need user ID and post ID.", "warn");
      return;
    }
    const { res, json } = await fetchJson(`/api/posts/${encodeURIComponent(postId)}/comments`, {
      method: "POST",
      body: JSON.stringify({
        authorId: Number(userId),
        authorType: "USER",
        content: "Depth probe from console",
        depthLevel: 21,
      }),
    });
    logLine(`Depth-21 probe → ${res.status} (expect 400)`, res.status === 400 ? "ok" : "warn");
    logLine(JSON.stringify(json), res.status === 400 ? "ok" : "warn");
  }

  async function handleCooldownSpam() {
    save();
    const { botId, postId } = requireIds();
    if (!botId || !postId) {
      logLine("Need bot ID and post ID.", "warn");
      return;
    }
    const body = (i) => ({
      authorId: Number(botId),
      authorType: "BOT",
      content: `Cooldown spam ${i} @ ${Date.now()}`,
      depthLevel: 0,
    });
    const first = await fetchJson(`/api/posts/${encodeURIComponent(postId)}/comments`, {
      method: "POST",
      body: JSON.stringify(body(1)),
    });
    logLine(`Bot comment A → ${first.res.status}`, first.res.ok ? "ok" : "warn");
    const second = await fetchJson(`/api/posts/${encodeURIComponent(postId)}/comments`, {
      method: "POST",
      body: JSON.stringify(body(2)),
    });
    logLine(`Bot comment B → ${second.res.status} (expect 429 if cooldown hit)`, second.res.status === 429 ? "ok" : "warn");
    await handleBundleRefresh();
  }

  async function handleBurst() {
    save();
    const { botId, postId } = requireIds();
    if (!botId || !postId) {
      logLine("Need bot ID and post ID.", "warn");
      return;
    }
    let n = Number(els.burstN.value);
    if (!Number.isFinite(n) || n < 1) n = 1;
    if (n > 30) n = 30;
    els.burstN.value = String(n);

    const tasks = Array.from({ length: n }, (_, i) =>
      fetchJson(`/api/posts/${encodeURIComponent(postId)}/comments`, {
        method: "POST",
        body: JSON.stringify({
          authorId: Number(botId),
          authorType: "BOT",
          content: `Burst ${i + 1}/${n}`,
          depthLevel: 0,
        }),
      })
    );
    const results = await Promise.all(tasks);
    const ok = results.filter((r) => r.res.ok).length;
    const r429 = results.filter((r) => r.res.status === 429).length;
    logLine(`Burst ${n} parallel: ${ok} ok, ${r429} rate-limited (429), others logged below`, "ok");
    results.forEach((r, i) => {
      if (!r.res.ok && r.res.status !== 429) {
        logLine(`Burst[${i}] → ${r.res.status} ${JSON.stringify(r.json)}`, "warn");
      }
    });
    await handleBundleRefresh();
  }

  function wire() {
    $("btn-ping").addEventListener("click", () => void handlePing());
    $("btn-clear-log").addEventListener("click", () => {
      els.log.innerHTML = "";
    });
    $("btn-create-user").addEventListener("click", () => void handleCreateUser());
    $("btn-create-bot").addEventListener("click", () => void handleCreateBot());
    $("btn-create-post").addEventListener("click", () => void handleCreatePost());
    $("btn-bundle-refresh").addEventListener("click", () => void handleBundleRefresh());
    $("btn-human-comment").addEventListener("click", () => void handleHumanComment());
    $("btn-bot-comment").addEventListener("click", () => void handleBotComment());
    $("btn-like").addEventListener("click", () => void handleLike());
    $("btn-depth-bad").addEventListener("click", () => void handleDepthBad());
    $("btn-cooldown-spam").addEventListener("click", () => void handleCooldownSpam());
    $("btn-burst").addEventListener("click", () => void handleBurst());

    ["input", "change"].forEach((ev) => {
      els.baseUrl.addEventListener(ev, () => {
        updateOriginPill();
        save();
      });
      els.userId.addEventListener(ev, save);
      els.botId.addEventListener(ev, save);
      els.postId.addEventListener(ev, save);
    });
  }

  load();
  wire();
  els.postContent.placeholder = `Example: Redis guardrails + virality — ${new Date().getFullYear()} demo post`;
  logLine("Ready. Leave base URL empty when this page is served by the API host, then use Create user → Create bot → Create post.");
})();
