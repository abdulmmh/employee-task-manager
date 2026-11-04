import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription, TimeoutError, finalize, takeUntil, timeout, timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/api/error-message';

@Component({ selector: 'app-login', templateUrl: './login.component.html' })
export class LoginComponent implements OnDestroy {
  readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]], password: ['', Validators.required] });
  busy = false;
  slow = false;
  timedOut = false;
  showPassword = false;
  error = '';
  private destroyed$ = new Subject<void>();
  private waitingTimer?: Subscription;

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    if (this.auth.isAuthenticated()) void this.router.navigateByUrl('/dashboard');
  }
  submit(): void {
    if (this.busy) return;
    this.error = '';
    this.slow = false;
    this.timedOut = false;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.value as { email: string; password: string };
    this.busy = true;
    this.waitingTimer = timer(20000).pipe(takeUntil(this.destroyed$)).subscribe(() => this.slow = true);
    this.auth.login(value.email, value.password).pipe(
      timeout({ first: 60000 }),
      takeUntil(this.destroyed$),
      finalize(() => { this.waitingTimer?.unsubscribe(); this.busy = false; this.slow = false; })
    ).subscribe({
      next: () => { this.form.reset(); void this.router.navigateByUrl('/dashboard'); },
      error: error => {
        this.timedOut = error instanceof TimeoutError;
        this.error = this.timedOut
          ? 'Sign-in timed out. Please try again. The server may still be starting.'
          : errorMessage(error);
      }
    });
  }
  ngOnDestroy(): void { this.waitingTimer?.unsubscribe(); this.destroyed$.next(); this.destroyed$.complete(); }
}
