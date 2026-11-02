import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { ToastService } from '../../shared/toast/toast.service';
import { LoginResponse } from '../api/models';

describe('AuthService session lifecycle', () => {
  let http: HttpTestingController;
  let router: jasmine.SpyObj<Router>;
  let toast: jasmine.SpyObj<ToastService>;

  function response(seconds = 1800): LoginResponse {
    const claims = btoa(JSON.stringify({ sub: '3', role: 'EMPLOYEE', exp: Math.floor(Date.now() / 1000) + seconds }));
    return { token: `header.${claims}.signature`, role: 'EMPLOYEE', name: 'Employee One' };
  }
  beforeEach(() => {
    sessionStorage.removeItem('etm.session');
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    router.navigateByUrl.and.returnValue(Promise.resolve(true));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['show']);
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule], providers: [
      { provide: Router, useValue: router }, { provide: ToastService, useValue: toast }
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { TestBed.inject(AuthService).logout(); sessionStorage.removeItem('etm.session'); http.verify(); });

  it('stores a valid login without persisting the password', () => {
    const auth = TestBed.inject(AuthService);
    auth.login(' emp1@demo.com ', 'Emp@123').subscribe();
    const request = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(request.request.body).toEqual({ email: 'emp1@demo.com', password: 'Emp@123' });
    request.flush(response());
    expect(auth.session?.userId).toBe(3);
    expect(auth.isAuthenticated()).toBeTrue();
    expect(sessionStorage.getItem('etm.session')).not.toContain('Emp@123');
  });

  it('restores an unexpired session on page refresh', () => {
    sessionStorage.setItem('etm.session', JSON.stringify(response()));
    const auth = TestBed.inject(AuthService);
    expect(auth.session?.role).toBe('EMPLOYEE');
    expect(auth.isAuthenticated()).toBeTrue();
  });

  it('discards an expired saved session', () => {
    sessionStorage.setItem('etm.session', JSON.stringify(response(-1)));
    expect(TestBed.inject(AuthService).session).toBeNull();
    expect(sessionStorage.getItem('etm.session')).toBeNull();
  });

  it('discards malformed browser storage', () => {
    sessionStorage.setItem('etm.session', '{broken');
    expect(TestBed.inject(AuthService).session).toBeNull();
  });

  it('uses token expiration to automatically end the session', fakeAsync(() => {
    const auth = TestBed.inject(AuthService);
    auth.login('emp1@demo.com', 'Emp@123').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush(response(1));
    tick(1100);
    expect(auth.session).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    expect(toast.show).toHaveBeenCalled();
  }));

  it('does not create a session for invalid credentials', () => {
    const auth = TestBed.inject(AuthService);
    auth.login('emp1@demo.com', 'wrong').subscribe({ error: () => undefined });
    http.expectOne(`${environment.apiUrl}/auth/login`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.session).toBeNull();
  });

  it('rejects a response whose role differs from the token payload', () => {
    const auth = TestBed.inject(AuthService);
    let failed = false;
    auth.login('emp1@demo.com', 'Emp@123').subscribe({ error: () => failed = true });
    http.expectOne(`${environment.apiUrl}/auth/login`).flush({ ...response(), role: 'ADMIN' });
    expect(failed).toBeTrue();
    expect(auth.session).toBeNull();
  });
});
