import { Injectable, computed, signal } from '@angular/core';
import { Observable, delay, of, throwError } from 'rxjs';
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

export const MOCK_USERS: Record<string, User> = {
  customer: {
    id: 'usr-mock-customer-1',
    email: 'customer@swiftdrop.lk',
    fullName: 'Kamal Perera',
    role: 'CUSTOMER',
    merchantId: null,
  },
  merchant: {
    id: 'usr-mock-merchant-1',
    email: 'merchant@daraz.lk',
    fullName: 'Daraz Store Manager',
    role: 'MERCHANT',
    merchantId: 'mch-5501',
  },
  courier: {
    id: 'usr-mock-courier-1',
    email: 'courier@swiftdrop.lk',
    fullName: 'Sunil Silva',
    role: 'COURIER',
    merchantId: null,
  },
  admin: {
    id: 'usr-mock-admin-1',
    email: 'admin@swiftdrop.lk',
    fullName: 'System Administrator',
    role: 'ADMIN',
    merchantId: null,
  },
};

@Injectable({
  providedIn: 'root',
})
export class MockAuthService {
  private readonly _accessToken = signal<string | null>(null);
  private readonly _currentUser = signal<User | null>(null);
  private readonly _isInitialized = signal<boolean>(false);

  readonly accessToken = this._accessToken.asReadonly();
  readonly currentUser = this._currentUser.asReadonly();
  readonly isInitialized = this._isInitialized.asReadonly();

  readonly isAuthenticated = computed(() => !!this._accessToken());
  readonly userRole = computed(() => this._currentUser()?.role ?? null);

  initializeSession(): Promise<boolean> {
    this._isInitialized.set(true);
    return Promise.resolve(this.isAuthenticated());
  }

  getCurrentUser(): Observable<User> {
    if (!this._currentUser()) {
      return throwError(() => ({ status: 401, error: 'Not authenticated' }));
    }
    return of(this._currentUser()!).pipe(delay(50));
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    const emailKey = Object.keys(MOCK_USERS).find(
      (k) => MOCK_USERS[k].email.toLowerCase() === credentials.email.toLowerCase()
    );

    if (emailKey && credentials.password === 'Password123!') {
      const user = MOCK_USERS[emailKey];
      const token = `mock-jwt-token-${user.role.toLowerCase()}-${Date.now()}`;
      this._accessToken.set(token);
      this._currentUser.set(user);

      return of({
        accessToken: token,
        tokenType: 'Bearer',
        expiresIn: 900,
        user,
      }).pipe(delay(200));
    }

    return throwError(() => ({
      status: 401,
      error: { code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' },
    })).pipe(delay(200));
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    const newUser: User = {
      id: `usr-mock-${Date.now()}`,
      email: data.email,
      fullName: data.fullName,
      role: 'CUSTOMER',
      merchantId: null,
    };
    const token = `mock-jwt-token-customer-${Date.now()}`;
    this._accessToken.set(token);
    this._currentUser.set(newUser);

    return of({
      accessToken: token,
      tokenType: 'Bearer',
      expiresIn: 900,
      user: newUser,
    }).pipe(delay(200));
  }

  forgotPassword(data: ForgotPasswordRequest): Observable<ForgotPasswordResponse> {
    return of({
      message: 'If an account exists with this email, password reset instructions have been sent.',
    }).pipe(delay(200));
  }

  resetPassword(data: ResetPasswordRequest): Observable<ResetPasswordResponse> {
    if (!data.token || data.token === 'invalid-token') {
      return throwError(() => ({
        status: 400,
        error: { code: 'INVALID_RESET_TOKEN', message: 'Invalid, expired, or already-used reset token.' },
      })).pipe(delay(200));
    }
    return of({
      message: 'Password has been successfully reset. Please log in with your new password.',
    }).pipe(delay(200));
  }

  refreshToken(): Observable<TokenRefreshResponse> {
    if (!this._currentUser()) {
      return throwError(() => ({ status: 401, error: 'Not authenticated' }));
    }
    const token = `mock-jwt-token-refreshed-${Date.now()}`;
    this._accessToken.set(token);
    return of({
      accessToken: token,
      tokenType: 'Bearer',
      expiresIn: 900,
    }).pipe(delay(100));
  }

  logout(): Observable<void> {
    this._accessToken.set(null);
    this._currentUser.set(null);
    return of(undefined).pipe(delay(100));
  }

  loginWithGoogle(): void {
    // Stub for mock
  }

  hasRole(role: string): boolean {
    return this.userRole()?.toUpperCase() === role.toUpperCase();
  }

  hasAnyRole(roles: string[]): boolean {
    const current = this.userRole()?.toUpperCase();
    if (!current) return false;
    return roles.map((r) => r.toUpperCase()).includes(current);
  }

  setSession(token: string, user: User): void {
    this._accessToken.set(token);
    this._currentUser.set(user);
  }

  clearAuth(): void {
    this._accessToken.set(null);
    this._currentUser.set(null);
  }
}
