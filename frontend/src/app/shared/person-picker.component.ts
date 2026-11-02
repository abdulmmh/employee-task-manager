import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged, finalize, map, takeUntil } from 'rxjs';
import { WorkspaceApiService } from '../core/api/workspace-api.service';
import { PersonOption } from '../core/api/models';
import { errorMessage } from '../core/api/error-message';

@Component({ selector: 'app-person-picker', template: `
  <div class="person-picker"><input type="search" class="form-control picker-search" [formControl]="search" [attr.aria-label]="'Search ' + label" placeholder="Search by name…" maxlength="100" [attr.disabled]="disabled ? '' : null">
    <select class="form-select" [attr.aria-label]="label" [disabled]="disabled || loading" [value]="selectedId || ''" (change)="select($event)">
      <option value="" [selected]="!selectedId">Select {{ label.toLowerCase() }}</option><option *ngIf="selectedId && !selectedVisible" [value]="selectedId" [selected]="true">{{ selectedName || ('Selected user #' + selectedId) }}</option>
      <option *ngFor="let person of options" [value]="person.id" [selected]="person.id === selectedId">{{ person.name }}</option>
    </select><span *ngIf="loading" class="field-hint" role="status">Loading options…</span><span *ngIf="!loading && !options.length && !error" class="field-hint">No matching active {{ label.toLowerCase() }} found.</span>
    <div *ngIf="error" class="field-error" role="alert">{{ error }} <button type="button" class="text-action" (click)="load()">Retry</button></div>
    <div class="picker-paging" *ngIf="totalPages > 1"><button type="button" [disabled]="loading || page === 0 || disabled" (click)="turn(-1)">Previous</button><span>Page {{ page + 1 }} / {{ totalPages }}</span><button type="button" [disabled]="loading || page + 1 >= totalPages || disabled" (click)="turn(1)">Next</button></div>
  </div>` })
export class PersonPickerComponent implements OnChanges, OnDestroy {
  @Input() kind: 'employees' | 'managers' = 'employees'; @Input() label = 'Employee';
  @Input() selectedId: number | null = null; @Input() selectedName = ''; @Input() disabled = false;
  @Output() selected = new EventEmitter<PersonOption | null>();
  search = new FormControl(''); options: PersonOption[] = []; page = 0; totalPages = 0; loading = false; error = '';
  private request?: Subscription; private destroyed$ = new Subject<void>(); private initialized = false;
  constructor(private api: WorkspaceApiService) {
    this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroyed$)).subscribe(() => { this.page = 0; this.load(); });
  }
  ngOnChanges(): void { if (!this.initialized) { this.initialized = true; this.load(); } }
  get selectedVisible(): boolean { return this.options.some(option => option.id === this.selectedId); }
  turn(delta: number): void { this.page += delta; this.load(); }
  select(event: Event): void {
    const id = Number((event.target as HTMLSelectElement).value);
    this.selected.emit(this.options.find(option => option.id === id) || (id === this.selectedId ? { id, name: this.selectedName } : null));
  }
  load(): void {
    if (this.disabled) return;
    this.request?.unsubscribe(); this.loading = true; this.error = '';
    const params = { page: this.page, size: 20, search: String(this.search.value || '') };
    const source = this.kind === 'employees' ? this.api.employees(params) : this.api.users({ ...params, role: 'MANAGER', active: true }).pipe(map(page => ({ ...page, content: page.content.map(user => ({ id: user.id, name: user.fullName })) })));
    this.request = source.pipe(finalize(() => this.loading = false)).subscribe({ next: result => { this.options = result.content; this.totalPages = result.totalPages; }, error: error => this.error = errorMessage(error) });
  }
  ngOnDestroy(): void { this.request?.unsubscribe(); this.destroyed$.next(); this.destroyed$.complete(); }
}
