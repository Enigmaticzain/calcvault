import path from "path";
import { fileURLToPath } from "url";
import { loadConfig } from "./config.js";
import { createLogger } from "./util/logger.js";
import { ensureDir } from "./util/fs.js";
import { BackupStore } from "./services/backup-store.js";
import { BridgeManager } from "./services/bridge-manager.js";
import { createHttpServer } from "./api/http-server.js";

const __filename = fileURLToPath(import.meta.url);

export async function createCompanionApp(overrides = {}) {
  const baseConfig = loadConfig();
  const config = {
    ...baseConfig,
    ...overrides,
    dataRoot: overrides.dataRoot || baseConfig.dataRoot,
    backupRoot: overrides.backupRoot || baseConfig.backupRoot,
    webRoot: overrides.webRoot || baseConfig.webRoot,
  };

  const logger = createLogger({ level: process.env.LOG_LEVEL || "info" });

  await ensureDir(config.dataRoot);
  await ensureDir(config.backupRoot);

  const backupStore = new BackupStore(config, logger);
  await backupStore.init();

  const bridgeManager = new BridgeManager(config, logger, backupStore);
  const httpServer = await createHttpServer({
    config,
    logger,
    bridgeManager,
    backupStore,
  });

  return {
    config,
    logger,
    bridgeManager,
    backupStore,
    launchToken: httpServer.launchToken,
    launchUrl: `http://${config.bindHost}:${config.port}/?token=${httpServer.launchToken}`,
    async start() {
      await httpServer.start();
      logger.info("CalcVault companion started", {
        host: config.bindHost,
        port: config.port,
      });
      logger.info("Open secure local UI URL", {
        launchUrl: `http://${config.bindHost}:${config.port}/?token=${httpServer.launchToken}`,
      });
    },
    async stop() {
      await httpServer.stop();
      logger.info("CalcVault companion stopped");
    },
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  const app = await createCompanionApp();
  await app.start();

  const shutdown = async () => {
    await app.stop();
    process.exit(0);
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}
