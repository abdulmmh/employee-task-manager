import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/auth/auth.guard';
import { LoginComponent } from './features/login/login.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { ShellComponent } from './layout/shell.component';
import { ReportsComponent } from './features/reports/reports.component';
import { UsersComponent } from './features/users/users.component';
import { TasksComponent } from './features/tasks/tasks.component';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: ShellComponent, canActivate: [AuthGuard], canActivateChild: [AuthGuard], children: [
    { path: 'dashboard', component: DashboardComponent },
    { path: 'users', component: UsersComponent, data: { roles: ['ADMIN'] } },
    { path: 'tasks', component: TasksComponent },
    { path: 'reports', component: ReportsComponent, data: { roles: ['ADMIN', 'MANAGER'] } },
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
  ] },
  { path: '**', redirectTo: 'dashboard' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
