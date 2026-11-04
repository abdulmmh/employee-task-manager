import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged, finalize, takeUntil } from 'rxjs';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { PersonOption, Query, Role, User, UserInput } from '../../core/api/models';
import { errorMessage } from '../../core/api/error-message';
import { applyServerErrors, fieldError, passwordBytes } from '../../shared/form-errors';
import { ToastService } from '../../shared/toast/toast.service';

@Component({ selector: 'app-users', templateUrl: './users.component.html' })
export class UsersComponent implements OnInit, OnDestroy {
  readonly filters = this.fb.group({ search: [''], role: [''] });
  readonly form = this.fb.group({ fullName: ['', [Validators.required, Validators.maxLength(100)]], email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]], password: ['', passwordBytes], role: ['EMPLOYEE', Validators.required], managerId: [null, Validators.required] });
  users: User[] = []; page = 0; readonly size = 10; total = 0; loading = false; error = ''; busy = false;
  editing?: User; editorOpen = false; formError = ''; managerName = ''; confirming?: User;
  readonly roles: Role[] = ['ADMIN', 'MANAGER', 'EMPLOYEE'];
  private listRequest?: Subscription; private destroyed$ = new Subject<void>();
  constructor(private fb: FormBuilder, private api: WorkspaceApiService, private toast: ToastService) {}
  ngOnInit(): void {
    this.load();
    this.filters.get('search')!.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroyed$)).subscribe(() => this.filter());
  }
  get employeeRole(): boolean { return this.form.get('role')!.value === 'EMPLOYEE'; }
  filter(): void { this.page = 0; this.load(); }
  get hasFilters(): boolean { return !!this.filters.get('search')!.value || !!this.filters.get('role')!.value; }
  clearFilters(): void { this.filters.reset({ search: '', role: '' }, { emitEvent: false }); this.filter(); }
  changePage(page: number): void { this.page = page; this.load(); }
  load(): void {
    this.listRequest?.unsubscribe(); this.loading = true; this.error = '';
    const values = this.filters.value as { search: string; role: string };
    const query: Query = { page: this.page, size: this.size, search: values.search || '', active: true };
    if (values.role) query['role'] = values.role;
    this.listRequest = this.api.users(query).pipe(finalize(() => this.loading = false)).subscribe({
      next: result => { this.users = result.content; this.total = result.totalElements; if (!this.users.length && this.page > 0) { this.page--; this.load(); } },
      error: error => this.error = errorMessage(error)
    });
  }
  open(user?: User): void {
    this.editing = user; this.managerName = ''; this.formError = '';
    this.form.reset({ fullName: user?.fullName || '', email: user?.email || '', password: '', role: user?.role || 'EMPLOYEE', managerId: user?.managerId || null });
    this.form.get('password')!.setValidators(user ? [passwordBytes] : [Validators.required, passwordBytes]);
    this.roleChanged(); this.form.get('password')!.updateValueAndValidity(); this.editorOpen = true;
  }
  roleChanged(): void {
    const manager = this.form.get('managerId')!;
    manager.setValidators(this.employeeRole ? [Validators.required] : []);
    if (!this.employeeRole) manager.setValue(null);
    manager.updateValueAndValidity();
  }
  selectManager(option: PersonOption | null): void {
    this.form.get('managerId')!.setValue(option?.id || null); this.managerName = option?.name || '';
    this.form.get('managerId')!.markAsTouched();
  }
  message(name: string): string { return fieldError(this.form, name); }
  close(): void { if (!this.busy) { this.editorOpen = false; this.form.reset(); this.editing = undefined; } }
  save(): void {
    this.form.markAllAsTouched(); if (this.form.invalid || this.busy) return;
    const input = this.form.value as Omit<UserInput, 'active'>;
    if (!input.fullName.trim()) { this.form.get('fullName')!.setErrors({ required: true }); return; }
    const creating = !this.editing;
    this.busy = true; this.formError = '';
    this.api.saveUser({ ...input, active: true, fullName: input.fullName.trim(), email: input.email.trim() }, this.editing?.id)
      .pipe(takeUntil(this.destroyed$), finalize(() => this.busy = false)).subscribe({
        next: () => { if (creating) this.page = 0; this.busy = false; this.close(); this.toast.show('success', 'User saved successfully.'); this.load(); },
        error: error => { applyServerErrors(this.form, error); this.formError = errorMessage(error); }
      });
  }
  deleteUser(): void {
    if (!this.confirming || this.busy) return;
    this.busy = true;
    this.api.deleteUser(this.confirming.id).pipe(takeUntil(this.destroyed$), finalize(() => this.busy = false)).subscribe({
      next: () => { this.confirming = undefined; this.toast.show('success', 'User deleted successfully.'); this.load(); },
      error: error => { this.confirming = undefined; this.toast.show('error', errorMessage(error)); }
    });
  }
  initials(name: string): string { return name.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase(); }
  ngOnDestroy(): void { this.listRequest?.unsubscribe(); this.destroyed$.next(); this.destroyed$.complete(); }
}
