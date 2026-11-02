import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { AuthGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('AuthGuard', () => {
  let auth: { session: { role: string }; isAuthenticated: jasmine.Spy };
  let router: Router;
  let guard: AuthGuard;
  beforeEach(() => {
    auth = { session: { role: 'EMPLOYEE' }, isAuthenticated: jasmine.createSpy().and.returnValue(true) };
    TestBed.configureTestingModule({ imports: [RouterTestingModule], providers: [{ provide: AuthService, useValue: auth }] });
    router = TestBed.inject(Router); guard = TestBed.inject(AuthGuard);
  });
  it('redirects anonymous visitors to login', () => {
    auth.isAuthenticated.and.returnValue(false);
    expect(router.serializeUrl(guard.canActivate(new ActivatedRouteSnapshot()) as UrlTree)).toBe('/login');
  });
  it('prevents employees entering the admin user page', () => {
    const route = new ActivatedRouteSnapshot(); route.data = { roles: ['ADMIN'] };
    expect(router.serializeUrl(guard.canActivateChild(route) as UrlTree)).toBe('/dashboard');
  });
  it('allows managers into the report page', () => {
    auth.session.role = 'MANAGER';
    const route = new ActivatedRouteSnapshot(); route.data = { roles: ['ADMIN', 'MANAGER'] };
    expect(guard.canActivateChild(route)).toBeTrue();
  });
});
