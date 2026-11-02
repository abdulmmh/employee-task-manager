import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from '../api/models';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate, CanActivateChild {
  constructor(private auth: AuthService, private router: Router) {}
  canActivate(route: ActivatedRouteSnapshot) {
    if (!this.auth.isAuthenticated()) return this.router.createUrlTree(['/login']);
    const roles = route.data['roles'] as Role[] | undefined;
    return !roles || (this.auth.session && roles.includes(this.auth.session.role))
      ? true : this.router.createUrlTree(['/dashboard']);
  }
  canActivateChild(route: ActivatedRouteSnapshot) { return this.canActivate(route); }
}
