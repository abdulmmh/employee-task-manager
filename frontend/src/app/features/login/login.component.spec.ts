import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject, finalize } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/auth/auth.service';
import { LoginResponse } from '../../core/api/models';

describe('LoginComponent slow request and retry behavior', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let pending: Subject<LoginResponse>;
  const response: LoginResponse = { token: 'test-token', role: 'ADMIN', name: 'Demo Admin' };

  beforeEach(waitForAsync(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated', 'login']);
    auth.isAuthenticated.and.returnValue(false);
    pending = new Subject<LoginResponse>();
    auth.login.and.returnValue(pending.asObservable());
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    router.navigateByUrl.and.returnValue(Promise.resolve(true));
    TestBed.configureTestingModule({
      imports: [CommonModule, ReactiveFormsModule],
      declarations: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    component.form.setValue({ email: 'admin@demo.com', password: 'Admin@123' });
    fixture.detectChanges();
  });
  afterEach(() => fixture.destroy());

  it('shows waiting guidance at 20 seconds and blocks duplicate submissions', fakeAsync(() => {
    component.submit();
    component.submit();
    tick(19999);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
    tick(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Please wait');
    expect(fixture.nativeElement.querySelector('.login-submit').disabled).toBeTrue();
    expect(auth.login).toHaveBeenCalledTimes(1);
    pending.complete();
  }));

  it('times out at 60 seconds, cancels the attempt and enables manual retry', fakeAsync(() => {
    const cancelled = jasmine.createSpy('cancelled');
    auth.login.and.returnValue(pending.pipe(finalize(cancelled)));
    component.submit();
    tick(59999);
    expect(component.busy).toBeTrue();
    tick(1);
    fixture.detectChanges();
    expect(cancelled).toHaveBeenCalledTimes(1);
    expect(component.busy).toBeFalse();
    expect(component.slow).toBeFalse();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Sign-in timed out');
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.login-submit');
    expect(button.disabled).toBeFalse();
    expect(button.textContent).toContain('Try again');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  }));

  for (const delay of [1000, 25000]) {
    it(`clears all waiting timers when login succeeds after ${delay} ms`, fakeAsync(() => {
      component.submit();
      tick(delay);
      pending.next(response);
      pending.complete();
      tick(60000);
      fixture.detectChanges();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
      expect(component.busy).toBeFalse();
      expect(component.slow).toBeFalse();
      expect(component.error).toBe('');
      expect(component.form.get('password')!.value).toBeNull();
      expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
    }));
  }

  it('keeps a wrong-password 401 as an inline credential error', fakeAsync(() => {
    component.submit();
    tick(20000);
    pending.error(new HttpErrorResponse({ status: 401, error: { message: 'Invalid email or password.' } }));
    tick(60000);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Invalid email or password.');
    expect(component.timedOut).toBeFalse();
    expect(component.busy).toBeFalse();
    expect(component.slow).toBeFalse();
    expect(component.form.get('password')!.value).toBe('Admin@123');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  }));

  it('does not automatically retry and ignores the old attempt after a manual retry', fakeAsync(() => {
    const retry = new Subject<LoginResponse>();
    auth.login.and.returnValues(pending.asObservable(), retry.asObservable());
    component.submit();
    tick(60000);
    expect(auth.login).toHaveBeenCalledTimes(1);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.login-submit');
    button.click();
    expect(auth.login).toHaveBeenCalledTimes(2);
    expect(component.error).toBe('');
    expect(component.timedOut).toBeFalse();
    pending.next(response);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    retry.next(response);
    retry.complete();
    tick(60000);
    expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
    expect(component.error).toBe('');
  }));

  it('cancels the request and timers when the page is destroyed', fakeAsync(() => {
    const cancelled = jasmine.createSpy('cancelled');
    auth.login.and.returnValue(pending.pipe(finalize(cancelled)));
    component.submit();
    fixture.destroy();
    tick(60000);
    pending.next(response);
    expect(cancelled).toHaveBeenCalledTimes(1);
    expect(component.slow).toBeFalse();
    expect(component.error).toBe('');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  }));
});
