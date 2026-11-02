import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ApiInterceptor } from './core/auth/api.interceptor';
import { ToastComponent } from './shared/toast/toast.component';
import { IconComponent } from './shared/icon.component';
import { LoginComponent } from './features/login/login.component';
import { ShellComponent } from './layout/shell.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { ReportsComponent } from './features/reports/reports.component';
import { UsersComponent } from './features/users/users.component';
import { TasksComponent } from './features/tasks/tasks.component';
import { PaginationComponent } from './shared/pagination.component';
import { ModalComponent } from './shared/modal.component';
import { PersonPickerComponent } from './shared/person-picker.component';
import { ClockComponent } from './shared/clock.component';

@NgModule({
  declarations: [
    AppComponent, ToastComponent, IconComponent, LoginComponent, ShellComponent,
    DashboardComponent, ReportsComponent, UsersComponent, TasksComponent,
    PaginationComponent, ModalComponent, PersonPickerComponent, ClockComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule, HttpClientModule, ReactiveFormsModule
  ],
  providers: [{ provide: HTTP_INTERCEPTORS, useClass: ApiInterceptor, multi: true }],
  bootstrap: [AppComponent]
})
export class AppModule { }
