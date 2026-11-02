import { Component } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast',
  template: `
    <section class="toast-stack" aria-label="Notifications" aria-live="polite" aria-atomic="false">
      <article *ngFor="let item of toast.items$ | async" class="etm-toast" [class]="'etm-toast toast-' + item.kind"
        [attr.role]="item.kind === 'error' ? 'alert' : 'status'">
        <span class="toast-symbol" aria-hidden="true">{{ item.kind === 'success' ? '✓' : item.kind === 'error' ? '×' : 'i' }}</span>
        <div><strong>{{ item.title }}</strong><p>{{ item.message }}</p></div>
        <button type="button" class="toast-close" aria-label="Dismiss notification" (click)="toast.dismiss(item.id)">×</button>
        <span class="toast-progress" [style.animation-duration.ms]="item.duration" aria-hidden="true"></span>
      </article>
    </section>`
})
export class ToastComponent { constructor(public toast: ToastService) {} }
