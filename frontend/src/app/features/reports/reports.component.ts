import { Component, OnDestroy } from '@angular/core';
import { Subject, finalize, takeUntil } from 'rxjs';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../shared/toast/toast.service';
import { errorMessage } from '../../core/api/error-message';
import { PdfPreviewService } from '../../shared/pdf-preview.service';

@Component({ selector: 'app-reports', templateUrl: './reports.component.html' })
export class ReportsComponent implements OnDestroy {
  busy = false;
  private destroyed$ = new Subject<void>();
  private pendingPreview?: Window;
  constructor(private api: WorkspaceApiService, public auth: AuthService, private toast: ToastService, private previewService: PdfPreviewService) {}
  download(preview = false): void {
    if (this.busy) return;
    if (preview) {
      const window = globalThis.window.open('', '_blank');
      if (!window) { this.toast.show('info', 'Allow pop-ups for this site to preview the report, or use Download PDF.'); return; }
      window.opener = null;
      window.document.title = 'Preparing task report';
      window.document.body.textContent = 'Preparing your PDF report…';
      this.pendingPreview = window;
    }
    this.busy = true;
    this.api.report().pipe(takeUntil(this.destroyed$), finalize(() => this.busy = false)).subscribe({
      next: blob => {
        if (preview) {
          const window = this.pendingPreview;
          this.pendingPreview = undefined;
          if (window && !window.closed) this.previewService.show(window, blob);
          return;
        }
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'tasks-report.pdf';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.toast.show('success', 'Your task report is ready to download.');
      }, error: error => { this.pendingPreview?.close(); this.pendingPreview = undefined; this.toast.show('error', errorMessage(error)); }
    });
  }
  ngOnDestroy(): void { this.pendingPreview?.close(); this.destroyed$.next(); this.destroyed$.complete(); }
}
