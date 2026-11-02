import { AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output } from '@angular/core';

@Component({ selector: 'app-modal', template: `
  <div class="dialog-backdrop" (click)="backdrop($event)"><section class="workspace-dialog" [class.danger-dialog]="danger" role="dialog" aria-modal="true" [attr.aria-label]="title" tabindex="-1">
    <header class="dialog-heading"><h2>{{ title }}</h2><button class="dialog-close" type="button" aria-label="Close dialog" [disabled]="busy" (click)="closed.emit()">×</button></header>
    <ng-content></ng-content>
  </section></div>` })
export class ModalComponent implements AfterViewInit, OnDestroy {
  @Input() title = ''; @Input() busy = false; @Input() danger = false;
  @Output() closed = new EventEmitter<void>();
  private oldFocus = document.activeElement as HTMLElement | null;
  private oldOverflow = document.body.style.overflow;
  constructor(private element: ElementRef<HTMLElement>) { document.body.style.overflow = 'hidden'; }
  ngAfterViewInit(): void { (this.focusable()[0] || this.element.nativeElement.querySelector<HTMLElement>('[role="dialog"]'))?.focus(); }
  backdrop(event: MouseEvent): void { if (event.target === event.currentTarget && !this.busy) this.closed.emit(); }
  @HostListener('document:keydown', ['$event']) keyboard(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.element.nativeElement.querySelector('.picker-expanded')) return;
    if (event.key === 'Escape' && !this.busy) { event.preventDefault(); this.closed.emit(); }
    if (event.key !== 'Tab') return;
    const items = this.focusable();
    if (!items.length) { event.preventDefault(); return; }
    const first = items[0], last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || !this.element.nativeElement.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }
  private focusable(): HTMLElement[] {
    return Array.from(this.element.nativeElement.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]')).filter(el => el.offsetParent !== null);
  }
  ngOnDestroy(): void { document.body.style.overflow = this.oldOverflow; this.oldFocus?.focus(); }
}
