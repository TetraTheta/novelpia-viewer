type CosmeticPayload = {
  css?: string;
  selectors?: string[];
};

declare global {
  interface Window {
    __npviewerApplyCosmetic?: (payload: CosmeticPayload | null | undefined) => void;
    __npviewerRefreshCosmetic?: () => void;
    __npviewerObserver?: MutationObserver;
    _AdFilter?: {
      getCosmetic(url: string): string;
    };
  }
}

const FILTER_STYLE_ELEMENT_ID = "npviewer-ad-filter-style";
const HIDDEN_MARKER_ATTRIBUTE = "data-npviewer-hidden";

function ensureStyle(cssText: string): void {
  let style = document.getElementById(FILTER_STYLE_ELEMENT_ID);
  if (!style) {
    style = document.createElement("style");
    style.id = FILTER_STYLE_ELEMENT_ID;
    (document.head || document.documentElement).appendChild(style);
  }
  style.textContent = cssText;
}

function markHidden(selectors: string[], root: ParentNode | null | undefined): void {
  if (!root || !("querySelectorAll" in root)) {
    return;
  }

  selectors.forEach((selector) => {
    try {
      root.querySelectorAll(selector).forEach((element) => {
        if (element.getAttribute(HIDDEN_MARKER_ATTRIBUTE) === "1") {
          return;
        }
        element.style.setProperty("display", "none", "important");
        element.setAttribute(HIDDEN_MARKER_ATTRIBUTE, "1");
      });
    } catch {
      // Ignore invalid selectors emitted by imperfect filter conversions.
    }
  });
}

function hideSelectors(selectors: string[]): void {
  if (selectors.length === 0) {
    return;
  }

  markHidden(selectors, document);

  if (!window.__npviewerObserver) {
    window.__npviewerObserver = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType === Node.ELEMENT_NODE) {
            markHidden(selectors, node as ParentNode);
          }
        });
      });
    });

    window.__npviewerObserver.observe(document.documentElement || document, {
      childList: true,
      subtree: true
    });
  }
}

export function applyCosmeticPayload(payload: CosmeticPayload | null | undefined): void {
  if (!payload) {
    return;
  }

  ensureStyle(payload.css || "");
  hideSelectors(payload.selectors || []);
}

export function refreshCosmeticPayload(): void {
  try {
    const cosmeticJson = window._AdFilter?.getCosmetic(location.href) || "";
    if (cosmeticJson) {
      applyCosmeticPayload(JSON.parse(cosmeticJson) as CosmeticPayload);
    }
  } catch {
    // Keep page rendering even if the bridge payload is malformed.
  }
}

export function bootstrapAdFilter(): void {
  window.__npviewerApplyCosmetic = applyCosmeticPayload;
  window.__npviewerRefreshCosmetic = refreshCosmeticPayload;
  refreshCosmeticPayload();
}
