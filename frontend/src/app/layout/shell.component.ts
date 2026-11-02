import { Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { Role } from '../core/api/models';

@Component({ selector: 'app-shell', templateUrl: './shell.component.html' })
export class ShellComponent {
  menuOpen = false;
  accountOpen = false;
  @ViewChild('accountButton') accountButton?: ElementRef<HTMLButtonElement>;
  readonly roleNames: Record<Role, string> = { ADMIN: 'Administrator', MANAGER: 'Manager', EMPLOYEE: 'Employee' };
  constructor(public auth: AuthService, private router: Router) {}
  get roleName(): string { return this.auth.session ? this.roleNames[this.auth.session.role] : ''; }
  get initials(): string { return (this.auth.session?.name || 'User').trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase(); }
  get pageName(): string {
    if (this.router.url.startsWith('/users')) return 'Users';
    if (this.router.url.startsWith('/tasks')) return this.auth.session?.role === 'EMPLOYEE' ? 'My tasks' : 'Tasks';
    return this.router.url.startsWith('/reports') ? 'Reports' : 'Dashboard';
  }
  @HostListener('document:click', ['$event']) outside(event: MouseEvent): void {
    if (!(event.target instanceof Element) || !event.target.closest('.account-menu-container')) this.accountOpen = false;
  }
  @HostListener('document:keydown.escape') escape(): void {
    if (this.accountOpen) { this.accountOpen = false; this.accountButton?.nativeElement.focus(); }
    this.menuOpen = false;
  }
}
