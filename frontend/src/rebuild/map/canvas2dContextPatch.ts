const PATCH_FLAG = "__combat_canvas2d_willReadFrequently_patch__";

/**
 * OpenLayers 在 pointermove 等路径上频繁用 getImageData 做命中检测；
 * Chromium 会建议使用 willReadFrequently: true 创建 2D 上下文（见 HTML 规范与 openlayers/openlayers#14175）。
 * 在首次创建地图前调用一次即可。
 */
export function ensureCanvas2dWillReadFrequentlyPatch(): void {
  if (typeof HTMLCanvasElement === "undefined" || typeof window === "undefined") return;
  const w = window as unknown as Record<string, boolean>;
  if (w[PATCH_FLAG]) return;
  w[PATCH_FLAG] = true;

  const proto = HTMLCanvasElement.prototype;
  const original = proto.getContext;

  proto.getContext = function (
    this: HTMLCanvasElement,
    contextId: string,
    ...rest: [unknown?, unknown?]
  ): RenderingContext | OffscreenRenderingContext | null {
    if (contextId === "2d") {
      const opts = rest[0];
      const merged: Record<string, unknown> =
        typeof opts === "object" && opts !== null && !Array.isArray(opts)
          ? { ...(opts as Record<string, unknown>) }
          : {};
      merged.willReadFrequently = true;
      return (original as (this: HTMLCanvasElement, id: string, o?: unknown) => RenderingContext | null).call(
        this,
        "2d",
        merged
      );
    }
    return (original as (this: HTMLCanvasElement, id: string, ...args: unknown[]) => RenderingContext | null).apply(
      this,
      [contextId, ...rest] as [string, ...unknown[]]
    );
  };
}
