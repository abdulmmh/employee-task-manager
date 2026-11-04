import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, Subscription, finalize, takeUntil } from 'rxjs';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { PersonOption, Query, Role, Task, TaskInput, TaskPriority, TaskStatus } from '../../core/api/models';
import { errorMessage } from '../../core/api/error-message';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../shared/toast/toast.service';
import { applyServerErrors, fieldError } from '../../shared/form-errors';

@Component({ selector: 'app-tasks', templateUrl: './tasks.component.html' })
export class TasksComponent implements OnInit, OnDestroy {
  readonly filters = this.fb.group({ status: [''], priority: [''], role: [''] });
  readonly form = this.fb.group({ title: ['', [Validators.required, Validators.maxLength(200)]], description: ['', Validators.maxLength(10000)], priority: ['MEDIUM', Validators.required], deadline: [''], assignedTo: [null, Validators.required] });
  readonly statuses: TaskStatus[] = ['PENDING', 'IN_PROGRESS', 'DONE']; readonly priorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];
  readonly roles: Role[] = ['ADMIN', 'MANAGER', 'EMPLOYEE'];
  tasks: Task[] = []; page = 0; readonly size = 10; total = 0; loading = false; error = ''; busy = false;
  assigneeFilter: PersonOption | null = null; editing?: Task; editorOpen = false; formError = ''; selectedName = ''; confirming?: Task;
  details?: Task; statusBusyId?: number;
  private listRequest?: Subscription; private destroyed$ = new Subject<void>();
  constructor(private fb: FormBuilder, private api: WorkspaceApiService, public auth: AuthService,
    private toast: ToastService, private route: ActivatedRoute, private router: Router) {}
  get canAssign(): boolean { return this.auth.session?.role === 'ADMIN' || this.auth.session?.role === 'MANAGER'; }
  get admin(): boolean { return this.auth.session?.role === 'ADMIN'; }
  get employee(): boolean { return this.auth.session?.role === 'EMPLOYEE'; }
  get frozenAssignee(): boolean { return !!this.editing && this.editing.status !== 'PENDING'; }
  ngOnInit(): void {
    this.load();
    if (this.route.snapshot.queryParamMap.get('create') === 'true' && this.canAssign) {
      this.open(); void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    }
  }
  filter(): void { this.page = 0; this.load(); }
  selectFilter(option: PersonOption | null): void { this.assigneeFilter = option; this.filter(); }
  get hasFilters(): boolean { return !!this.assigneeFilter || !!this.filters.get('status')!.value || !!this.filters.get('priority')!.value || (this.admin && !!this.filters.get('role')!.value); }
  clearFilters(): void { this.filters.reset({ status: '', priority: '', role: '' }); this.assigneeFilter = null; this.filter(); }
  changePage(page: number): void { this.page = page; this.load(); }
  load(): void {
    this.listRequest?.unsubscribe(); this.loading = true; this.error = '';
    const values = this.filters.value as { status: string; priority: string; role: string };
    const query: Query = { page: this.page, size: this.size };
    if (values.status) query['status'] = values.status;
    if (values.priority) query['priority'] = values.priority;
    if (this.admin && values.role) query['role'] = values.role;
    if (this.assigneeFilter) query['assignee'] = this.assigneeFilter.id;
    this.listRequest = this.api.tasks(query).pipe(finalize(() => this.loading = false)).subscribe({
      next: result => { this.tasks = result.content; this.total = result.totalElements; if (!this.tasks.length && this.page > 0) { this.page--; this.load(); } },
      error: error => this.error = errorMessage(error)
    });
  }
  open(task?: Task): void {
    this.editing = task; this.formError = ''; this.selectedName = task?.assigneeName || '';
    this.form.reset({ title: task?.title || '', description: task?.description || '', priority: task?.priority || 'MEDIUM', deadline: task?.deadline || '', assignedTo: task?.assignedTo || null });
    this.editorOpen = true;
  }
  selectAssignee(option: PersonOption | null): void {
    this.form.get('assignedTo')!.setValue(option?.id || null); this.form.get('assignedTo')!.markAsTouched(); this.selectedName = option?.name || '';
  }
  message(name: string): string { return fieldError(this.form, name); }
  close(): void { if (!this.busy) { this.editorOpen = false; this.form.reset(); this.editing = undefined; } }
  save(): void {
    this.form.markAllAsTouched(); if (this.form.invalid || this.busy) return;
    const values = this.form.value as TaskInput;
    if (!values.title.trim()) { this.form.get('title')!.setErrors({ required: true }); return; }
    this.busy = true; this.formError = '';
    const input = { ...values, title: values.title.trim(), description: values.description || null, deadline: values.deadline || null };
    this.api.saveTask(input, this.editing?.id).pipe(takeUntil(this.destroyed$), finalize(() => this.busy = false)).subscribe({
      next: () => { this.busy = false; this.close(); this.toast.show('success', 'Task saved successfully.'); this.load(); },
      error: error => { applyServerErrors(this.form, error); this.formError = errorMessage(error); }
    });
  }
  advance(task: Task): void {
    if (this.statusBusyId || task.status === 'DONE') return;
    const status: TaskStatus = task.status === 'PENDING' ? 'IN_PROGRESS' : 'DONE';
    this.statusBusyId = task.id;
    this.api.changeStatus(task.id, status).pipe(takeUntil(this.destroyed$), finalize(() => this.statusBusyId = undefined)).subscribe({
      next: () => { this.toast.show('success', status === 'DONE' ? 'Task completed. Nice work!' : 'Task is now in progress.'); this.load(); },
      error: error => this.toast.show('error', errorMessage(error))
    });
  }
  delete(): void {
    if (!this.confirming || this.busy) return;
    this.busy = true;
    this.api.deleteTask(this.confirming.id).pipe(takeUntil(this.destroyed$), finalize(() => this.busy = false)).subscribe({
      next: () => { this.confirming = undefined; this.toast.show('success', 'Task deleted successfully.'); this.load(); },
      error: error => { this.confirming = undefined; this.toast.show('error', errorMessage(error)); }
    });
  }
  statusLabel(status: TaskStatus): string { return status === 'IN_PROGRESS' ? 'In progress' : status === 'PENDING' ? 'Pending' : 'Done'; }
  overdue(task: Task): boolean {
    const now = new Date(); const local = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    return !!task.deadline && task.status !== 'DONE' && task.deadline < local;
  }
  ngOnDestroy(): void { this.listRequest?.unsubscribe(); this.destroyed$.next(); this.destroyed$.complete(); }
}
