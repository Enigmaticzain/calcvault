import { app, BrowserWindow, shell } from "electron";
import { createCompanionApp } from "./main.js";

let mainWindow = null;
let companionServer = null;

async function createWindow() {
  // Start local companion server with USB/ADB-only behavior forced
  companionServer = await createCompanionApp({
    bindHost: "127.0.0.1",
    allowNetworkTransport: false,
    disableUsbTransport: false,
  });

  await companionServer.start();

  mainWindow = new BrowserWindow({
    width: 1080,
    height: 720,
    title: "CalcVault Companion (USB/ADB Only)",
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
    },
  });

  // Normal TLS verification is strictly preserved (default Electron behavior)
  mainWindow.loadURL(companionServer.launchUrl);

  // Prevent unexpected external window navigation
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith("http://") || url.startsWith("https://")) {
      shell.openExternal(url);
    }
    return { action: "deny" };
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

app.whenReady().then(createWindow);

app.on("window-all-closed", async () => {
  if (companionServer) {
    await companionServer.stop();
  }
  if (process.platform !== "darwin") {
    app.quit();
  }
});

app.on("activate", () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});
