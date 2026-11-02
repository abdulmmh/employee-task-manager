import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, finalize, takeUntil } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/api/error-message';

@Component({ selector: 'app-login', templateUrl: './login.component.html' })
export class LoginComponent implements OnDestroy {
  readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]], password: ['', Validators.required] });
  busy = false;
  showPassword = false;
  error = '';
  private destroyed$ = new Subject<void>();

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    if (this.auth.isAuthenticated()) void this.router.navigateByUrl('/dashboard');
  }
  submit(): void {
    if (this.busy) return;
    this.error = '';
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.value as { email: string; password: string };
    this.busy = true;
    this.auth.login(value.email, value.password).pipe(takeUntil(this.destroyed$), finalize(() => this.busy = false))
      .subscribe({ next: () => { this.form.reset(); void this.router.navigateByUrl('/dashboard'); }, error: error => this.error = errorMessage(error) });
  }
  ngOnDestroy(): void { this.destroyed$.next(); this.destroyed$.complete(); }
}
