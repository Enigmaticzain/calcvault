import path from "node:path";
import { app, BrowserWindow, dialog } from "electron";
import { createCompanionApp } from "../src/main.js";
import { isPermittedDownloadWindow } from "./window-policy.js";

const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) app.quit();

let companion;
let mainWindow;
let shutdownStarted = false;

async function createMainWindow() {
  const origin = `http://${companion.config.bindHost}:${companion.config.port}`;
  const window = new BrowserWindow({
    title: "CalcVault Companion",
    width: 1180,
    height: 820,
    minWidth: 900,
    minHeight: 640,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: "#10131c",
    icon: path.join(app.getAppPath(), "assets", "calcvault-companion.png"),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
    },
  });

  window.webContents.setWindowOpenHandler(({ url }) => {
    // The current UI opens only attachment-download URLs in a second window.
    // Deny every other popup, including external links and arbitrary local paths.
    if (!isPermittedDownloadWindow(url, origin)) return { action: "deny" };
    return {
      action: "allow",
      overrideBrowserWindowOptions: {
        width: 720,
        height: 520,
        autoHideMenuBar: true,
        webPreferences: {
          nodeIntegration: false,
          contextIsolation: true,
          sandbox: true,
          webSecurity: true,
        },
      },
    };
  });

  window.webContents.on("will-navigate", (event, target) => {
    try {
      if (new URL(target).origin !== origin) event.preventDefault();
    } catch {
      event.preventDefault();
    }
  });
  window.webContents.on("will-attach-webview", (event) => event.preventDefault());
  window.webContents.session.setPermissionRequestHandler((_webContents, _permission, callback) => callback(false));

  window.once("ready-to-show", () => window.show());
  window.on("closed", () => {
    if (mainWindow === window) mainWindow = undefined;
  });
  window.webContents.on("render-process-gone", (_event, details) => {
    console.error("CalcVault Companion renderer exited", details);
    if (!window.isDestroyed()) window.reload();
  });

  mainWindow = window;
  await window.loadURL(companion.launchUrl);
}

async function startDesktopApp() {
  try {
    const userData = path.join(app.getPath("userData"), "companion");
    companion = await createCompanionApp({
      bindHost: "127.0.0.1",
      port: 0,
      dataRoot: path.join(userData, "data"),
      backupRoot: path.join(userData, "data", "backups"),
    });
    await companion.start();
    await createMainWindow();
  } catch (error) {
    console.error("CalcVault Companion failed to start", error);
    await dialog.showMessageBox({
      type: "error",
      title: "CalcVault Companion could not start",
      message: "The local companion service could not start.",
      detail: error?.message || String(error),
    });
    const failedCompanion = companion;
    companion = undefined;
    if (failedCompanion) await failedCompanion.stop().catch(() => {});
    app.quit();
  }
}

if (gotSingleInstanceLock) {
  app.on("second-instance", () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(startDesktopApp);

  app.on("activate", () => {
    if (companion && !mainWindow) createMainWindow().catch((error) => console.error(error));
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });

  app.on("before-quit", (event) => {
    if (!companion || shutdownStarted) return;
    event.preventDefault();
    shutdownStarted = true;
    const stoppingCompanion = companion;
    companion = undefined;
    stoppingCompanion.stop()
      .catch((error) => console.error("CalcVault Companion shutdown failed", error))
      .finally(() => app.quit());
  });
}
