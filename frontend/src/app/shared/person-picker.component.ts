import { Component, ElementRef, EventEmitter, HostListener, Input, OnChanges, OnDestroy, Output, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged, finalize, map, takeUntil, timeout } from 'rxjs';
import { WorkspaceApiService } from '../core/api/workspace-api.service';
import { PersonOption } from '../core/api/models';
import { errorMessage } from '../core/api/error-message';

@Component({ selector: 'app-person-picker', template: `
  <div class="person-picker">
    <button #trigger type="button" class="picker-trigger" [class.picker-expanded]="opened" [disabled]="disabled" [attr.aria-label]="label" [attr.aria-expanded]="opened" (click)="toggle()"><span [class.muted]="!selectedId">{{ selectedLabel }}</span><app-icon name="chevron"></app-icon></button>
    <div class="picker-popup" *ngIf="opened"><input #searchBox type="search" class="form-control picker-search" [formControl]="search" [attr.aria-label]="'Search ' + label" placeholder="Type a name to filter…" maxlength="100" (keydown.arrowdown)="focusFirst($event)">
      <div class="picker-state" *ngIf="loading" role="status"><span class="spinner-border spinner-border-sm"></span> Loading…</div>
      <div class="picker-state" *ngIf="!loading && !options.length && !error" role="status">No matching {{ label.toLowerCase() }} found.</div>
      <div *ngIf="error" class="picker-state field-error" role="alert">{{ error }} <button type="button" class="text-action" (click)="load()">Retry</button></div>
      <div class="picker-options" *ngIf="!loading && !error"><button *ngFor="let person of options" type="button" class="picker-option" [class.picked]="person.id === selectedId" (click)="choose(person)"><span>{{ person.name }}</span><span *ngIf="person.id === selectedId" aria-label="Selected">✓</span></button></div>
      <div class="picker-paging" *ngIf="!loading && !error && totalPages > 1"><button type="button" [disabled]="page === 0" (click)="turn(-1)">Previous</button><span>{{ page + 1 }} / {{ totalPages }}</span><button type="button" [disabled]="page + 1 >= totalPages" (click)="turn(1)">Next</button></div>
    </div>
  </div>` })
export class PersonPickerComponent implements OnChanges, OnDestroy {
  @Input() kind: 'employees' | 'managers' = 'employees'; @Input() label = 'Employee';
  @Input() selectedId: number | null = null; @Input() selectedName = ''; @Input() disabled = false;
  @Input() placeholder = '';
  @Output() selected = new EventEmitter<PersonOption | null>();
  @ViewChild('trigger') trigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('searchBox') set searchBox(element: ElementRef<HTMLInputElement> | undefined) { element?.nativeElement.focus(); }
  search = new FormControl(''); options: PersonOption[] = []; page = 0; totalPages = 0; loading = false; error = ''; opened = false;
  private request?: Subscription; private labelRequest?: Subscription; private destroyed$ = new Subject<void>();
  private resolved?: PersonOption;
  constructor(private api: WorkspaceApiService, private host: ElementRef<HTMLElement>) {
    this.search.valueChanges.pipe(debounceTime(250), distinctUntilChanged(), takeUntil(this.destroyed$)).subscribe(() => { if (this.opened) { this.page = 0; this.load(); } });
  }
  ngOnChanges(): void {
    if (this.disabled) this.close(false);
    if (this.kind === 'managers' && this.selectedId && !this.selectedName) {
      this.labelRequest?.unsubscribe();
      this.labelRequest = this.api.user(this.selectedId).pipe(timeout(10000)).subscribe({ next: user => this.resolved = { id: user.id, name: user.fullName }, error: () => undefined });
    }
  }
  get selectedLabel(): string {
    if (!this.selectedId) return this.placeholder || `Select ${this.label.toLowerCase()}`;
    return this.selectedName || (this.resolved?.id === this.selectedId ? this.resolved.name : '')
      || this.options.find(option => option.id === this.selectedId)?.name || `${this.label} #${this.selectedId}`;
  }
  toggle(): void {
    if (this.disabled) return;
    if (this.opened) { this.close(); return; }
    this.opened = true; this.page = 0; this.search.setValue('', { emitEvent: false }); this.load();
  }
  choose(person: PersonOption): void { this.resolved = person; this.selected.emit(person); this.close(); }
  close(restoreFocus = true): void {
    const wasOpen = this.opened;
    this.opened = false; this.request?.unsubscribe(); this.loading = false;
    if (wasOpen && restoreFocus) this.trigger?.nativeElement.focus();
  }
  @HostListener('document:click', ['$event']) outside(event: MouseEvent): void {
    if (this.opened && event.target instanceof Node && !this.host.nativeElement.contains(event.target)) this.close(false);
  }
  @HostListener('document:keydown.escape', ['$event']) escape(event: KeyboardEvent): void {
    if (this.opened) { event.preventDefault(); event.stopImmediatePropagation(); this.close(); }
  }
  @HostListener('focusout', ['$event']) blur(event: FocusEvent): void {
    if (event.relatedTarget instanceof Node && !this.host.nativeElement.contains(event.relatedTarget)) this.close(false);
  }
  focusFirst(event: Event): void { event.preventDefault(); this.host.nativeElement.querySelector<HTMLButtonElement>('.picker-option')?.focus(); }
  turn(delta: number): void { this.page += delta; this.load(); }
  load(): void {
    if (this.disabled || !this.opened) return;
    this.request?.unsubscribe(); this.loading = true; this.error = ''; this.options = [];
    const params = { page: this.page, size: 20, search: String(this.search.value || '') };
    const source = this.kind === 'employees' ? this.api.employees(params) : this.api.users({ ...params, role: 'MANAGER', active: true }).pipe(map(page => ({ ...page, content: page.content.map(user => ({ id: user.id, name: user.fullName })) })));
    this.request = source.pipe(timeout(10000), finalize(() => this.loading = false)).subscribe({ next: result => { this.options = result.content; this.totalPages = result.totalPages; }, error: error => this.error = error?.name === 'TimeoutError' ? 'The server is taking too long. Please retry.' : errorMessage(error) });
  }
  ngOnDestroy(): void { this.request?.unsubscribe(); this.labelRequest?.unsubscribe(); this.destroyed$.next(); this.destroyed$.complete(); }
}
