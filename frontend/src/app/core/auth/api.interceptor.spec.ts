import { HttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { ApiInterceptor } from './api.interceptor';

describe('ApiInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  let auth: { session: { token: string } | null; isAuthenticated: jasmine.Spy; expire: jasmine.Spy };
  beforeEach(() => {
    auth = { session: { token: 'access-token' }, isAuthenticated: jasmine.createSpy().and.returnValue(true), expire: jasmine.createSpy() };
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule], providers: [
      { provide: AuthService, useValue: auth }, { provide: HTTP_INTERCEPTORS, useClass: ApiInterceptor, multi: true }
    ] });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });
  afterEach(() => requests.verify());
  it('attaches bearer tokens to backend requests', () => {
    http.get(`${environment.apiUrl}/tasks`).subscribe();
    const request = requests.expectOne(`${environment.apiUrl}/tasks`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    request.flush({});
  });
  it('never sends the token to external or lookalike API URLs', () => {
    for (const url of ['https://example.com/tasks', `${environment.apiUrl}-other/tasks`]) {
      http.get(url).subscribe();
      const request = requests.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBeFalse();
      request.flush({});
    }
  });
  it('does not attach a previous token to login', () => {
    http.post(`${environment.apiUrl}/auth/login`, {}).subscribe();
    const request = requests.expectOne(`${environment.apiUrl}/auth/login`);
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });
  it('ends the current session on API 401', () => {
    http.get(`${environment.apiUrl}/tasks`).subscribe({ error: () => undefined });
    requests.expectOne(`${environment.apiUrl}/tasks`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.expire).toHaveBeenCalled();
  });
  it('preserves the session on API 403', () => {
    http.get(`${environment.apiUrl}/users`).subscribe({ error: () => undefined });
    requests.expectOne(`${environment.apiUrl}/users`).flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.expire).not.toHaveBeenCalled();
  });
  it('ignores stale 401 responses after a new login', () => {
    http.get(`${environment.apiUrl}/tasks`).subscribe({ error: () => undefined });
    auth.session = { token: 'new-access-token' };
    requests.expectOne(`${environment.apiUrl}/tasks`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.expire).not.toHaveBeenCalled();
  });
});
