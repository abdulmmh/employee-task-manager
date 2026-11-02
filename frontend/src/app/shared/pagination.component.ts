import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({ selector: 'app-pagination', template: `
  <div class="task-pagination" *ngIf="total"><span>Showing {{ page * size + 1 }}–{{ end }} of {{ total }}</span>
    <nav class="pagination-controls" aria-label="Result pages">
      <button class="page-button" type="button" aria-label="Previous page" [disabled]="busy || page === 0" (click)="changed.emit(page - 1)">‹</button>
      <button *ngFor="let number of pages" class="page-button" type="button" [class.current]="page === number" [disabled]="busy" [attr.aria-current]="page === number ? 'page' : null" [attr.aria-label]="'Page ' + (number + 1)" (click)="changed.emit(number)">{{ number + 1 }}</button>
      <button class="page-button" type="button" aria-label="Next page" [disabled]="busy || page + 1 >= totalPages" (click)="changed.emit(page + 1)">›</button>
    </nav>
  </div>` })
export class PaginationComponent {
  @Input() page = 0; @Input() size = 10; @Input() total = 0; @Input() busy = false;
  @Output() changed = new EventEmitter<number>();
  get totalPages(): number { return Math.ceil(this.total / this.size); }
  get end(): number { return Math.min(this.total, (this.page + 1) * this.size); }
  get pages(): number[] {
    const first = Math.max(0, Math.min(this.page - 2, this.totalPages - 5));
    return Array.from({ length: Math.min(5, this.totalPages) }, (_, i) => first + i);
  }
}
