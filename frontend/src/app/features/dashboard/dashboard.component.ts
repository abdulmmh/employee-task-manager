import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, finalize, forkJoin, takeUntil } from 'rxjs';
import { WorkspaceApiService } from '../../core/api/workspace-api.service';
import { Dashboard, Task } from '../../core/api/models';
import { errorMessage } from '../../core/api/error-message';
import { AuthService } from '../../core/auth/auth.service';

@Component({ selector: 'app-dashboard', templateUrl: './dashboard.component.html' })
export class DashboardComponent implements OnInit, OnDestroy {
  stats?: Dashboard;
  tasks: Task[] = [];
  page = 0;
  readonly pageSize = 4;
  totalTasks = 0;
  totalPages = 0;
  loading = false;
  error = '';
  readonly today = new Date();
  private destroyed$ = new Subject<void>();
  constructor(private api: WorkspaceApiService, public auth: AuthService) {}
  ngOnInit(): void { this.load(); }
  get greeting(): string { const hour = new Date().getHours(); return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'; }
  get firstName(): string { return this.auth.session?.name.trim().split(/\s+/)[0] || 'there'; }
  get taskHeading(): string { return this.stats?.role === 'EMPLOYEE' ? 'My tasks' : this.stats?.role === 'MANAGER' ? 'Assigned tasks' : 'Recent tasks'; }
  get firstRow(): number { return this.totalTasks ? this.page * this.pageSize + 1 : 0; }
  get lastRow(): number { return this.page * this.pageSize + this.tasks.length; }
  get pages(): number[] {
    const start = Math.max(0, Math.min(this.page - 2, this.totalPages - 5));
    return Array.from({ length: Math.min(5, this.totalPages) }, (_, index) => start + index);
  }
  goToPage(page: number): void {
    if (this.loading || page < 0 || page >= this.totalPages || page === this.page) return;
    this.page = page;
    this.load();
  }
  get chartBackground(): string {
    if (!this.stats?.totalTasks) return '#e8eef5';
    const pending = this.stats.tasksByStatus.pending / this.stats.totalTasks * 360;
    const progress = pending + this.stats.tasksByStatus.inProgress / this.stats.totalTasks * 360;
    return `conic-gradient(#f8bd51 0deg ${pending}deg, #5a9bf5 ${pending}deg ${progress}deg, #48b68b ${progress}deg 360deg)`;
  }
  load(): void {
    if (this.loading) return;
    this.loading = true;
    this.error = '';
    forkJoin({ stats: this.api.dashboard(), tasks: this.api.recentTasks(this.page, this.pageSize) })
      .pipe(takeUntil(this.destroyed$), finalize(() => this.loading = false))
      .subscribe({ next: result => {
        this.stats = result.stats;
        this.tasks = result.tasks.content;
        this.totalTasks = result.tasks.totalElements;
        this.totalPages = result.tasks.totalPages;
      }, error: error => this.error = errorMessage(error) });
  }
  ngOnDestroy(): void { this.destroyed$.next(); this.destroyed$.complete(); }
}
