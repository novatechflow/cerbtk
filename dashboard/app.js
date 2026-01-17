const demoState = {
  chain: {
    index: 42,
    hash: "a3f1c9b7e4f2c0d9b1a6c87f1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e",
  },
  anchor: "cerbtk:42:a3f1c9b7e4f2c0d9b1a6c87f1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e",
  timeline: [
    {
      time: "08:04Z",
      title: "Firmware signed",
      body: "core-image-minimal / rev-a",
    },
    {
      time: "08:10Z",
      title: "Device registered",
      body: "device-0412",
    },
    {
      time: "08:14Z",
      title: "Ownership bound",
      body: "operator-xyz",
    },
  ],
};

const els = {
  baseUrl: document.getElementById("baseUrl"),
  chainStatus: document.getElementById("chainStatus"),
  chainHead: document.getElementById("chainHead"),
  chainAnchor: document.getElementById("chainAnchor"),
  payloadInput: document.getElementById("payloadInput"),
  hashInput: document.getElementById("hashInput"),
  deviceIdInput: document.getElementById("deviceIdInput"),
  nonceDeviceId: document.getElementById("nonceDeviceId"),
  nonceValue: document.getElementById("nonceValue"),
  nonceExpiry: document.getElementById("nonceExpiry"),
  responseOutput: document.getElementById("responseOutput"),
  timeline: document.getElementById("timeline"),
  modeDemo: document.getElementById("modeDemo"),
  modeLive: document.getElementById("modeLive"),
  timelineMode: document.getElementById("timelineMode"),
};

let liveMode = false;

function setMode(isLive) {
  liveMode = isLive;
  els.modeLive.classList.toggle("active", isLive);
  els.modeDemo.classList.toggle("active", !isLive);
  updateStatus(isLive ? "Live" : "Demo", isLive ? "warn" : "ok");
  els.timelineMode.textContent = isLive ? "Live feed" : "Demo feed";
  if (!isLive) {
    renderChain(demoState.chain, demoState.anchor);
    renderTimeline(demoState.timeline);
  } else {
    renderTimeline([]);
  }
}

function updateStatus(text, state) {
  els.chainStatus.textContent = text;
  els.chainStatus.className = `status ${state}`;
}

function renderChain(chain, anchor) {
  els.chainHead.textContent = `${chain.index} / ${chain.hash.slice(0, 10)}...`;
  els.chainAnchor.textContent = anchor;
}

function renderResponse(data) {
  els.responseOutput.textContent = JSON.stringify(data, null, 2);
}

function renderTimeline(items) {
  els.timeline.innerHTML = "";
  items.forEach((item) => {
    const card = document.createElement("div");
    card.className = "timeline-item";
    card.innerHTML = `
      <strong>${item.time}</strong>
      <div>
        <div>${item.title}</div>
        <div class="hint">${item.body}</div>
      </div>
    `;
    els.timeline.appendChild(card);
  });
}

function samplePayload() {
  return {
    deviceId: "device-123",
    owner: "operator-xyz",
    timestamp: new Date().toISOString(),
    publicKey: "base64-public-key",
    signature: "base64-signature",
    firmwareHash: "sha256:deadbeef",
    buildId: "yocto-2026-01-17",
    recipe: "core-image-minimal",
    boardRev: "rev-a",
    nonce: "demo-nonce",
    algorithm: "SHA256withRSA",
  };
}

async function fetchJson(path, options) {
  const url = `${els.baseUrl.value.replace(/\/$/, "")}${path}`;
  const res = await fetch(url, options);
  const data = await res.json();
  if (!res.ok) {
    throw data;
  }
  return data;
}

async function refreshChain() {
  if (!liveMode) {
    renderChain(demoState.chain, demoState.anchor);
    renderResponse({ mode: "demo", chain: demoState.chain });
    return;
  }
  try {
    const head = await fetchJson("/chain/head");
    const anchor = await fetchJson("/chain/anchor");
    renderChain(head, anchor.anchor || "--");
    renderResponse({ head, anchor });
  } catch (err) {
    updateStatus("Offline", "bad");
    renderResponse(err);
  }
}

async function validateChain() {
  if (!liveMode) {
    updateStatus("Valid", "ok");
    renderResponse({ valid: true });
    return;
  }
  try {
    const data = await fetchJson("/chain/validate");
    updateStatus(data.valid ? "Valid" : "Invalid", data.valid ? "ok" : "bad");
    renderResponse(data);
  } catch (err) {
    updateStatus("Error", "bad");
    renderResponse(err);
  }
}

async function submitPayload() {
  let payload;
  try {
    payload = JSON.parse(els.payloadInput.value || "{}");
  } catch (err) {
    renderResponse({ error: "invalid_json" });
    return;
  }

  if (!liveMode) {
    demoState.chain.index += 1;
    demoState.chain.hash = Math.random().toString(16).slice(2).padEnd(64, "0");
    demoState.anchor = `cerbtk:${demoState.chain.index}:${demoState.chain.hash}`;
    demoState.timeline.unshift({
      time: new Date().toISOString().slice(11, 16) + "Z",
      title: "Device registered",
      body: payload.deviceId || "device",
    });
    renderChain(demoState.chain, demoState.anchor);
    renderTimeline(demoState.timeline.slice(0, 6));
    renderResponse({ mode: "demo", mined: demoState.chain });
    return;
  }

  try {
    const data = await fetchJson("/device/write", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    renderResponse(data);
  } catch (err) {
    renderResponse(err);
  }
}

async function lookupByHash() {
  const hash = els.hashInput.value.trim();
  if (!hash) return;
  if (!liveMode) {
    renderResponse({ mode: "demo", hash, data: samplePayload() });
    return;
  }
  try {
    const data = await fetchJson(`/device/${hash}`);
    renderResponse(data);
  } catch (err) {
    renderResponse(err);
  }
}

async function lookupByDeviceId() {
  const deviceId = els.deviceIdInput.value.trim();
  if (!deviceId) return;
  if (!liveMode) {
    renderResponse({ mode: "demo", deviceId, data: samplePayload() });
    return;
  }
  try {
    const data = await fetchJson(`/device/id/${deviceId}`);
    renderResponse(data);
  } catch (err) {
    renderResponse(err);
  }
}

async function requestNonce() {
  const deviceId = els.nonceDeviceId.value.trim();
  if (!deviceId) {
    renderResponse({ error: "missing_device_id" });
    return;
  }
  if (!liveMode) {
    els.nonceValue.textContent = "demo-nonce-" + Math.random().toString(36).slice(2, 8);
    els.nonceExpiry.textContent = "expires: 5m";
    renderResponse({ mode: "demo", deviceId, nonce: els.nonceValue.textContent });
    return;
  }
  try {
    const data = await fetchJson(`/device/nonce/${deviceId}`);
    els.nonceValue.textContent = data.nonce || "--";
    els.nonceExpiry.textContent = `expires: ${data.expiresAt || "--"}`;
    renderResponse(data);
  } catch (err) {
    renderResponse(err);
  }
}

function loadSample() {
  if (!liveMode) {
    const payload = samplePayload();
    els.payloadInput.value = JSON.stringify(payload, null, 2);
    return;
  }

  fetchJson("/dashboard/sample")
    .then((payload) => {
      els.payloadInput.value = JSON.stringify(payload, null, 2);
    })
    .catch((err) => {
      renderResponse(err);
    });
}

function clearOutput() {
  els.responseOutput.textContent = "{}";
}

function init() {
  els.modeDemo.addEventListener("click", () => setMode(false));
  els.modeLive.addEventListener("click", () => setMode(true));
  document.getElementById("btnRefresh").addEventListener("click", refreshChain);
  document.getElementById("btnValidate").addEventListener("click", validateChain);
  document.getElementById("btnSample").addEventListener("click", loadSample);
  document.getElementById("btnSubmit").addEventListener("click", submitPayload);
  document.getElementById("btnLookupHash").addEventListener("click", lookupByHash);
  document.getElementById("btnLookupId").addEventListener("click", lookupByDeviceId);
  document.getElementById("btnNonce").addEventListener("click", requestNonce);
  document.getElementById("btnClear").addEventListener("click", clearOutput);
  renderTimeline(demoState.timeline);
  setMode(false);
}

init();
