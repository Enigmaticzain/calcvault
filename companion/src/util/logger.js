const LEVELS = {
  error: 0,
  warn: 1,
  info: 2,
  debug: 3,
};

function toLevel(value) {
  const key = String(value || "info").toLowerCase();
  return LEVELS[key] ?? LEVELS.info;
}

function format(level, message, meta) {
  const timestamp = new Date().toISOString();
  const base = `[${timestamp}] [${level.toUpperCase()}] ${message}`;
  if (!meta) return base;
  return `${base} ${JSON.stringify(meta)}`;
}

export function createLogger(options = {}) {
  const activeLevel = toLevel(options.level || process.env.LOG_LEVEL);

  function emit(level, message, meta) {
    if (toLevel(level) > activeLevel) return;
    const line = format(level, message, meta);
    if (level === "error") {
      console.error(line);
      return;
    }
    console.log(line);
  }

  return {
    error(message, meta) {
      emit("error", message, meta);
    },
    warn(message, meta) {
      emit("warn", message, meta);
    },
    info(message, meta) {
      emit("info", message, meta);
    },
    debug(message, meta) {
      emit("debug", message, meta);
    },
  };
}
