import { Injectable, Injector } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

@Injectable()
export class ApiInterceptor implements HttpInterceptor {
  constructor(private injector: Injector) {}
  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const root = environment.apiUrl.replace(/\/$/, '');
    if (request.url !== root && !request.url.startsWith(root + '/')) return next.handle(request);
    const auth = this.injector.get(AuthService);
    const login = request.url === root + '/auth/login';
    const token = !login && auth.isAuthenticated() ? auth.session?.token : undefined;
    const outgoing = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
    return next.handle(outgoing).pipe(catchError((error: unknown) => {
      // A delayed response from an older session must not terminate a newer login.
      if (error instanceof HttpErrorResponse && error.status === 401 && token && auth.session?.token === token) auth.expire();
      return throwError(() => error);
    }));
  }
}
