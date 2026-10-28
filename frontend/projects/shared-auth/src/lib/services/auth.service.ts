import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  TokenRefreshResponse,
  User,
} from '../models/auth.models';

/**
 * Core Authentication Service for SwiftDrop frontend ecosystem.
 *
 * Adheres strictly to ADR 004:
 * - Access token stored IN MEMORY ONLY via Angular Signals.
 * - NEVER persisted in localStorage, sessionStorage, or IndexedDB.
 * - Refresh token is managed via httpOnly, Secure, SameSite cookie by auth-service.
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/auth';

  // In-memory token storage (ADR 004)
  private readonly _accessToken = signal<string | null>(null);
  private readonly _currentUser = signal<User | null>(null);

  readonly accessToken = this._accessToken.asReadonly();
  readonly currentUser = this._currentUser.asReadonly();

  readonly isAuthenticated = computed(() => !!this._accessToken());
  readonly userRole = computed(() => this._currentUser()?.role ?? null);

  /**
   * Authenticates user with credentials.
   */
  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, credentials, {
        withCredentials: true,
      })
      .pipe(
        tap((response) => {
          this._accessToken.set(response.accessToken);
          this._currentUser.set(response.user);
        })
      );
  }

  /**
   * Self-service customer registration.
   */
  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, data, {
        withCredentials: true,
      })
      .pipe(
        tap((response) => {
          this._accessToken.set(response.accessToken);
          this._currentUser.set(response.user);
        })
      );
  }

  /**
   * Performs silent token rotation via httpOnly cookie.
   */
  refreshToken(): Observable<TokenRefreshResponse> {
    return this.http
      .post<TokenRefreshResponse>(
        `${this.baseUrl}/refresh`,
        {},
        { withCredentials: true }
      )
      .pipe(
        tap((response) => {
          this._accessToken.set(response.accessToken);
        }),
        catchError((err) => {
          this.clearAuth();
          return throwError(() => err);
        })
      );
  }

  /**
   * Logs out user, invalidating backend token session and clearing memory.
   */
  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(
        tap(() => this.clearAuth()),
        catchError((err) => {
          this.clearAuth();
          return throwError(() => err);
        })
      );
  }

  /**
   * Manually sets in-memory session (e.g. after OAuth redirect handoff).
   */
  setSession(token: string, user: User): void {
    this._accessToken.set(token);
    this._currentUser.set(user);
  }

  /**
   * Clears in-memory session state.
   */
  clearAuth(): void {
    this._accessToken.set(null);
    this._currentUser.set(null);
  }

  hasRole(role: string): boolean {
    return this.userRole()?.toUpperCase() === role.toUpperCase();
  }

  hasAnyRole(roles: string[]): boolean {
    const current = this.userRole()?.toUpperCase();
    if (!current) return false;
    return roles.map((r) => r.toUpperCase()).includes(current);
  }
}
