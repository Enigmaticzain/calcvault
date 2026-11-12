export function isPermittedDownloadWindow(rawUrl, origin) {
  try {
    const url = new URL(rawUrl);
    return url.origin === origin && /^\/api\/files\/[^/]+\/download$/.test(url.pathname);
  } catch {
    return false;
  }
}
