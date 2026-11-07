import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, throwError, shareReplay, finalize, of, switchMap, map } from 'rxjs';
import {
  AuthResponse,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  ResetPasswordResponse,
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
 * - Provides single-flight token refresh to prevent token-family revocation on concurrency.
 * - Restores user session and role on application startup via initializeSession().
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
  private readonly _isInitialized = signal<boolean>(false);

  readonly accessToken = this._accessToken.asReadonly();
  readonly currentUser = this._currentUser.asReadonly();
  readonly isInitialized = this._isInitialized.asReadonly();

  readonly isAuthenticated = computed(() => !!this._accessToken());
  readonly userRole = computed(() => this._currentUser()?.role ?? null);

  // In-flight refresh coordination (single-flight)
  private refreshInProgress$: Observable<TokenRefreshResponse> | null = null;
  private initPromise: Promise<boolean> | null = null;

  /**
   * Restores session during Angular startup using httpOnly refresh cookie.
   * Ensures single execution and caches the initialization promise.
   */
  initializeSession(): Promise<boolean> {
    if (this._isInitialized()) {
      return Promise.resolve(this.isAuthenticated());
    }

    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = new Promise<boolean>((resolve) => {
      this.refreshToken().subscribe({
        next: () => {
          this._isInitialized.set(true);
          this.initPromise = null;
          resolve(true);
        },
        error: () => {
          this._isInitialized.set(true);
          this.initPromise = null;
          resolve(false);
        },
      });
    });

    return this.initPromise;
  }

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
          this._isInitialized.set(true);
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
          this._isInitialized.set(true);
        })
      );
  }

  /**
   * Request password reset instructions via email (M3).
   */
  forgotPassword(data: ForgotPasswordRequest): Observable<ForgotPasswordResponse> {
    return this.http.post<ForgotPasswordResponse>(`${this.baseUrl}/password/forgot`, data);
  }

  /**
   * Reset password with token (M3).
   */
  resetPassword(data: ResetPasswordRequest): Observable<ResetPasswordResponse> {
    return this.http.post<ResetPasswordResponse>(`${this.baseUrl}/password/reset`, data);
  }

  /**
   * Retrieves current authenticated user profile from /api/v1/auth/me.
   */
  getCurrentUser(token?: string): Observable<User> {
    const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
    return this.http
      .get<User>(`${this.baseUrl}/me`, { headers, withCredentials: true })
      .pipe(
        tap((user) => {
          this._currentUser.set(user);
        })
      );
  }

  /**
   * Performs silent token rotation via httpOnly cookie.
   * Deduplicates concurrent callers into a single in-flight request.
   */
  refreshToken(): Observable<TokenRefreshResponse> {
    if (this.refreshInProgress$) {
      return this.refreshInProgress$;
    }

    this.refreshInProgress$ = this.http
      .post<TokenRefreshResponse>(
        `${this.baseUrl}/refresh`,
        {},
        { withCredentials: true }
      )
      .pipe(
        tap((response) => {
          this._accessToken.set(response.accessToken);
          if (response.user) {
            this._currentUser.set(response.user);
          }
        }),
        switchMap((response) => {
          if (response.user) {
            return of(response);
          }
          return this.getCurrentUser(response.accessToken).pipe(
            map(() => response),
            catchError(() => of(response))
          );
        }),
        catchError((err) => {
          this.clearAuth();
          return throwError(() => err);
        }),
        finalize(() => {
          this.refreshInProgress$ = null;
        }),
        shareReplay(1)
      );

    return this.refreshInProgress$;
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
    this._isInitialized.set(true);
  }

  /**
   * Clears in-memory session state.
   */
  clearAuth(): void {
    this._accessToken.set(null);
    this._currentUser.set(null);
    this.refreshInProgress$ = null;
    this.initPromise = null;
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
