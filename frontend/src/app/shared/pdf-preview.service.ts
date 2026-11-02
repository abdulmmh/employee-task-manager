import { Injectable, OnDestroy } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PdfPreviewService implements OnDestroy {
  private previews = new Map<Window, string>();
  private timer?: ReturnType<typeof setInterval>;
  show(window: Window, blob: Blob): void {
    const url = URL.createObjectURL(blob);
    window.location.replace(url);
    this.previews.set(window, url);
    if (!this.timer) this.timer = setInterval(() => this.cleanup(), 15000);
  }
  private cleanup(): void {
    this.previews.forEach((url, window) => {
      if (window.closed) { URL.revokeObjectURL(url); this.previews.delete(window); }
    });
    if (!this.previews.size) { clearInterval(this.timer); this.timer = undefined; }
  }
  ngOnDestroy(): void { clearInterval(this.timer); this.previews.forEach(url => URL.revokeObjectURL(url)); this.previews.clear(); }
}
