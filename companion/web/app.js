const config = window.__CV_CONFIG__ || {};
const API_TOKEN = String(config.apiToken || "");

const state = {
  status: null,
  usbDevices: [],
  backups: [],
  files: [],
  pairing: null,
};

const el = {
  connectionBadge: document.getElementById("connectionBadge"),
  sessionBadge: document.getElementById("sessionBadge"),
  dashboardGrid: document.getElementById("dashboardGrid"),
  usbDeviceSelect: document.getElementById("usbDeviceSelect"),
  networkUrlInput: document.getElementById("networkUrlInput"),
  pairingCode: document.getElementById("pairingCode"),
  pairingExpiry: document.getElementById("pairingExpiry"),
  pairingQr: document.getElementById("pairingQr"),
  backupTableBody: document.getElementById("backupTableBody"),
  filesTableBody: document.getElementById("filesTableBody"),
  uploadInput: document.getElementById("uploadInput"),
  sessionLog: document.getElementById("sessionLog"),
};

function log(message, meta = null) {
  const stamp = new Date().toISOString();
  const line = meta ? `${stamp} ${message} ${JSON.stringify(meta)}` : `${stamp} ${message}`;
  el.sessionLog.textContent = `${line}\n${el.sessionLog.textContent}`.slice(0, 15_000);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      "x-calcvault-ui-token": API_TOKEN,
      ...(options.headers || {}),
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.ok === false) {
    throw new Error(data.error || `request_failed_${response.status}`);
  }
  return data;
}

function bytesText(value) {
  const bytes = Number(value || 0);
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let index = 0;
  let amount = bytes;
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024;
    index += 1;
  }
  return `${amount.toFixed(index === 0 ? 0 : 2)} ${units[index]}`;
}

function when(value) {
  if (!value) return "-";
  return new Date(Number(value)).toLocaleString();
}

function updateBadges() {
  const connected = Boolean(state.status?.connected);
  const sessionActive = Boolean(state.status?.sessionActive);

  el.connectionBadge.textContent = connected ? "Connected" : "Disconnected";
  el.connectionBadge.className = `badge ${connected ? "online" : "offline"}`;

  el.sessionBadge.textContent = sessionActive ? "Session Active" : "Session Inactive";
  el.sessionBadge.className = `badge ${sessionActive ? "active" : "idle"}`;
}

function renderDashboard() {
  const status = state.status || {};
  const device = status.device || {};
  const html = [
    ["Transport", status.transport || "none"],
    ["Endpoint", status.endpoint || "-"],
    ["Device", device.name || "Not paired"],
    ["Device ID", device.id || "-"],
    ["Session Expires", when(status.sessionExpiresAt)],
    ["Backups Stored", String(state.backups.length)],
  ].map(([key, value]) => `<div class="pair-card"><label>${key}</label><div>${value}</div></div>`).join("");

  el.dashboardGrid.innerHTML = html;
}

function renderUsbDevices() {
  if (!state.usbDevices.length) {
    el.usbDeviceSelect.innerHTML = `<option value="">No USB devices found</option>`;
    return;
  }

  el.usbDeviceSelect.innerHTML = state.usbDevices
    .map((device) => `<option value="${device.serial}">${device.serial} (${device.model})</option>`)
    .join("");
}

function renderPairing() {
  if (!state.pairing) {
    el.pairingCode.textContent = "-";
    el.pairingExpiry.textContent = "";
    el.pairingQr.textContent = "No active pairing";
    return;
  }

  el.pairingCode.textContent = state.pairing.code;
  el.pairingExpiry.textContent = `Expires: ${when(state.pairing.expiresAt)}`;
  el.pairingQr.innerHTML = state.pairing.qrSvg || "";
}

function renderBackups() {
  if (!state.backups.length) {
    el.backupTableBody.innerHTML = `<tr><td colspan="6">No backups found.</td></tr>`;
    return;
  }

  el.backupTableBody.innerHTML = state.backups.map((backup) => {
    const integrity = backup.sha256 ? `${backup.sha256.slice(0, 12)}...` : "pending";
    return `
      <tr>
        <td>${backup.backupId}</td>
        <td>${backup.deviceId}</td>
        <td>${when(backup.createdAt)}</td>
        <td>${bytesText(backup.size)}</td>
        <td>${integrity}</td>
        <td><button class="btn secondary" data-restore="${backup.backupId}">Restore</button></td>
      </tr>
    `;
  }).join("");

  for (const button of el.backupTableBody.querySelectorAll("button[data-restore]")) {
    button.addEventListener("click", async () => {
      try {
        const backupId = button.getAttribute("data-restore");
        log("Restore requested", { backupId });
        await api(`/api/backups/${encodeURIComponent(backupId)}/restore`, {
          method: "POST",
        });
        log("Restore completed", { backupId });
      } catch (error) {
        log("Restore failed", { error: error.message });
        alert(`Restore failed: ${error.message}`);
      }
    });
  }
}

function renderFiles() {
  if (!state.files.length) {
    el.filesTableBody.innerHTML = `<tr><td colspan="5">No files available from phone metadata.</td></tr>`;
    return;
  }

  el.filesTableBody.innerHTML = state.files.map((file) => `
    <tr>
      <td>${file.fileId || file.id || "-"}</td>
      <td>${file.name || "-"}</td>
      <td>${bytesText(file.size)}</td>
      <td>${when(file.modifiedAt || file.updatedAt)}</td>
      <td>
        <button class="btn secondary" data-download="${file.fileId || file.id || ""}" data-mode="encrypted">Encrypted</button>
        <button class="btn secondary" data-download="${file.fileId || file.id || ""}" data-mode="decrypted">Decrypted</button>
      </td>
    </tr>
  `).join("");

  for (const button of el.filesTableBody.querySelectorAll("button[data-download]")) {
    button.addEventListener("click", () => {
      const fileId = button.getAttribute("data-download");
      const mode = button.getAttribute("data-mode");
      const url = `/api/files/${encodeURIComponent(fileId)}/download?mode=${encodeURIComponent(mode)}&apiToken=${encodeURIComponent(API_TOKEN)}`;
      log("Download started", { fileId, mode });
      window.open(url, "_blank", "noopener");
    });
  }
}

function refreshAllViews() {
  updateBadges();
  renderDashboard();
  renderUsbDevices();
  renderPairing();
  renderBackups();
  renderFiles();
}

async function loadStatus() {
  const result = await api("/api/status");
  state.status = result.status;
}

async function loadUsbDevices() {
  const result = await api("/api/usb/devices");
  state.usbDevices = result.devices || [];
}

async function loadBackups() {
  const result = await api("/api/backups");
  state.backups = result.backups || [];
}

async function loadFiles() {
  const result = await api("/api/files");
  state.files = result.files || [];
}

async function connectUsb() {
  const serial = el.usbDeviceSelect.value;
  if (!serial) {
    alert("Select a USB device first.");
    return;
  }
  await api("/api/connection/connect", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ transport: "usb", serial }),
  });
  log("USB connected", { serial });
}

async function connectNetwork() {
  const url = String(el.networkUrlInput.value || "").trim();
  if (!url) {
    alert("Enter a ws:// or wss:// URL from phone companion screen.");
    return;
  }

  await api("/api/connection/connect", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ transport: "network", url }),
  });

  log("Network connected", { url });
}

async function disconnect() {
  await api("/api/connection/disconnect", {
    method: "POST",
  });
  state.pairing = null;
  log("Disconnected");
}

async function startPairing() {
  const result = await api("/api/pairing/start", {
    method: "POST",
  });
  state.pairing = result.pairing;
  log("Pairing started", { pairingId: result.pairing.pairingId });
}

async function createBackup() {
  const result = await api("/api/backups/create", {
    method: "POST",
  });
  log("Backup created", result.backup);
  await loadBackups();
}

async function uploadSelectedFiles() {
  const files = Array.from(el.uploadInput.files || []);
  if (!files.length) {
    alert("Select at least one file to upload.");
    return;
  }

  for (const file of files) {
    const form = new FormData();
    form.append("file", file);
    log("Uploading file", { name: file.name, size: file.size });
    await api("/api/files/upload", {
      method: "POST",
      body: form,
    });
    log("Uploaded", { name: file.name });
  }

  await loadFiles();
}

function bindActions() {
  document.getElementById("refreshUsbBtn").addEventListener("click", async () => {
    try {
      await loadUsbDevices();
      refreshAllViews();
    } catch (error) {
      log("USB refresh failed", { error: error.message });
      alert(error.message);
    }
  });

  document.getElementById("connectUsbBtn").addEventListener("click", async () => {
    try {
      await connectUsb();
      await loadStatus();
      refreshAllViews();
    } catch (error) {
      log("USB connect failed", { error: error.message });
      alert(error.message);
    }
  });

  document.getElementById("connectNetworkBtn").addEventListener("click", async () => {
    try {
      await connectNetwork();
      await loadStatus();
      refreshAllViews();
    } catch (error) {
      log("Network connect failed", { error: error.message });
      alert(error.message);
    }
  });

  document.getElementById("disconnectBtn").addEventListener("click", async () => {
    try {
      await disconnect();
      await loadStatus();
      refreshAllViews();
    } catch (error) {
      log("Disconnect failed", { error: error.message });
    }
  });

  document.getElementById("startPairingBtn").addEventListener("click", async () => {
    try {
      await startPairing();
      refreshAllViews();
    } catch (error) {
      log("Pairing start failed", { error: error.message });
      alert(error.message);
    }
  });

  document.getElementById("createBackupBtn").addEventListener("click", async () => {
    try {
      await createBackup();
      refreshAllViews();
    } catch (error) {
      log("Backup creation failed", { error: error.message });
      alert(error.message);
    }
  });

  document.getElementById("refreshBackupsBtn").addEventListener("click", async () => {
    try {
      await loadBackups();
      refreshAllViews();
    } catch (error) {
      log("Backup refresh failed", { error: error.message });
      alert(error.message);
    }
  });

  document.getElementById("refreshFilesBtn").addEventListener("click", async () => {
    try {
      await loadFiles();
      refreshAllViews();
    } catch (error) {
      log("File metadata refresh failed", { error: error.message });
      alert(error.message);
    }
  });

  document.getElementById("uploadBtn").addEventListener("click", async () => {
    try {
      await uploadSelectedFiles();
      refreshAllViews();
    } catch (error) {
      log("Upload failed", { error: error.message });
      alert(error.message);
    }
  });
}

function connectEventStream() {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  const ws = new WebSocket(`${protocol}://${window.location.host}/ui/events?token=${encodeURIComponent(API_TOKEN)}`);

  ws.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === "status") {
        state.status = message.payload;
        refreshAllViews();
      }
      if (message.type === "backup_created") {
        loadBackups().then(refreshAllViews).catch(() => {});
      }
      if (message.type === "operation_event") {
        log("Operation event", message.payload);
      }
    } catch (_) {
      // Ignore malformed events.
    }
  };

  ws.onclose = () => {
    setTimeout(connectEventStream, 1500);
  };
}

async function bootstrap() {
  bindActions();

  try {
    await Promise.all([loadStatus(), loadUsbDevices(), loadBackups()]);
    refreshAllViews();
    connectEventStream();
    log("Companion UI ready");
  } catch (error) {
    log("Bootstrap failed", { error: error.message });
    alert(`Bootstrap failed: ${error.message}`);
  }
}

bootstrap();
