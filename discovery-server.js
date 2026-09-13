'use strict';

const { startCompatibilityProxy } = require('./wrapper-utils');

function startServer(options = {}) {
  const runtime = startCompatibilityProxy({
    ...options,
    port: Number(options.port ?? process.env.PORT ?? 8081),
  });

  runtime.server.on('error', () => process.exit(1));

  const shutdown = () => {
    runtime.stop().finally(() => process.exit(0));
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);

  return runtime;
}

module.exports = { startServer };

if (require.main === module) {
  startServer();
}
