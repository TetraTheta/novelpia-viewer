declare global {
  interface Window {
    __npviewerRestoreScrollPosition?: () => void;
    _ScrollRestore?: {
      getScrollY(url: string): number;
    };
  }
}

export function restoreScrollPosition(): void {
  const y = window._ScrollRestore?.getScrollY(location.href) || 0;
  if (y <= 0) {
    return;
  }

  const applyScroll = (): void => {
    window.scrollTo(0, y);
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", applyScroll, { once: true });
  } else {
    applyScroll();
  }
}

export function bootstrapScrollRestore(): void {
  window.__npviewerRestoreScrollPosition = restoreScrollPosition;
  restoreScrollPosition();
}
