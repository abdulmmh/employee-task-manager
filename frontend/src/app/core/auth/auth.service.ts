import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginResponse, Role, Session } from '../api/models';
import { ToastService } from '../../shared/toast/toast.service';

const STORAGE_KEY = 'etm.session';
const ROLES: Role[] = ['ADMIN', 'MANAGER', 'EMPLOYEE'];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private state = new BehaviorSubject<Session | null>(null);
  readonly session$ = this.state.asObservable();
  private expiryTimer?: ReturnType<typeof setTimeout>;

  constructor(private http: HttpClient, private router: Router, private toast: ToastService) {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (stored) this.accept(JSON.parse(stored) as LoginResponse);
    } catch {
      this.clear();
    }
  }

  get session(): Session | null { return this.state.value; }

  login(email: string, password: string) {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, { email: email.trim(), password })
      .pipe(tap(response => this.accept(response)));
  }

  isAuthenticated(): boolean {
    if (this.session && this.session.expiresAt > Date.now()) return true;
    if (this.session) this.expire();
    return false;
  }

  logout(): void {
    this.clear();
    void this.router.navigateByUrl('/login');
  }

  expire(): void {
    const hadSession = !!this.session;
    this.clear();
    if (hadSession) this.toast.show('info', 'Your session has expired. Please sign in again.', 'Session expired');
    void this.router.navigateByUrl('/login');
  }

  private accept(response: LoginResponse): void {
    if (typeof response.token !== 'string' || typeof response.name !== 'string' || !ROLES.includes(response.role)) {
      throw new Error('Invalid login response.');
    }
    const payload = response.token.split('.')[1];
    if (!payload) throw new Error('Invalid access token.');
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number; sub?: string; role?: Role };
    const userId = Number(decoded.sub);
    if (typeof decoded.exp !== 'number' || !Number.isFinite(decoded.exp) || decoded.exp * 1000 <= Date.now()
      || !Number.isSafeInteger(userId) || userId <= 0 || decoded.role !== response.role) {
      throw new Error('Invalid or expired access token.');
    }
    const session: Session = { ...response, userId, expiresAt: decoded.exp * 1000 };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(response));
    this.state.next(session);
    clearTimeout(this.expiryTimer);
    this.expiryTimer = setTimeout(() => {
      if (this.session && this.session.expiresAt > Date.now()) this.accept(this.session);
      else this.expire();
    }, Math.min(session.expiresAt - Date.now(), 2147483647));
  }

  private clear(): void {
    clearTimeout(this.expiryTimer);
    this.state.next(null);
    try { sessionStorage.removeItem(STORAGE_KEY); } catch { /* Storage may be disabled by the browser. */ }
  }
}
