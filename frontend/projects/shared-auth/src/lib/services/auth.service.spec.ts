import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthResponse, TokenRefreshResponse, User } from '../models/auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  const mockUser: User = {
    id: 'usr-1',
    email: 'test@swiftdrop.lk',
    fullName: 'Test User',
    role: 'CUSTOMER',
    merchantId: null,
  };

  const mockMerchantUser: User = {
    id: 'usr-2',
    email: 'merchant@daraz.lk',
    fullName: 'Daraz Store Manager',
    role: 'MERCHANT',
    merchantId: 'mch-5501',
  };

  const mockAuthResponse: AuthResponse = {
    accessToken: 'mock-jwt-token-abc',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: mockUser,
  };

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should initialize with no in-memory token, unauthenticated, and uninitialized', () => {
    expect(service.accessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.isInitialized()).toBe(false);
  });

  it('login should store access token in memory only and set currentUser and isInitialized', () => {
    service.login({ email: 'test@swiftdrop.lk', password: 'Password123!' }).subscribe((res) => {
      expect(res).toEqual(mockAuthResponse);
    });

    const req = httpTesting.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush(mockAuthResponse);

    // Verify token stored in memory signal
    expect(service.accessToken()).toBe('mock-jwt-token-abc');
    expect(service.currentUser()).toEqual(mockUser);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.isInitialized()).toBe(true);
    expect(service.hasRole('CUSTOMER')).toBe(true);

    // ADR 004 verification: MUST NOT be in localStorage or sessionStorage
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('token')).toBeNull();
  });

  it('register should store access token and user in memory upon success', () => {
    service.register({
      email: 'new@swiftdrop.lk',
      password: 'Password123!',
      fullName: 'New User',
    }).subscribe((res) => {
      expect(res).toEqual(mockAuthResponse);
    });

    const req = httpTesting.expectOne('/api/v1/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush(mockAuthResponse);

    expect(service.accessToken()).toBe('mock-jwt-token-abc');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.isInitialized()).toBe(true);
  });

  it('refreshToken should rotate in-memory access token and restore user from response', () => {
    const refreshResponse: TokenRefreshResponse = {
      accessToken: 'new-rotated-token-xyz',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    };

    service.refreshToken().subscribe((res) => {
      expect(res).toEqual(refreshResponse);
    });

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush(refreshResponse);

    expect(service.accessToken()).toBe('new-rotated-token-xyz');
    expect(service.currentUser()).toEqual(mockUser);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('refreshToken should fallback to /api/v1/auth/me if user is not in refresh payload', () => {
    const refreshResponse: TokenRefreshResponse = {
      accessToken: 'new-rotated-token-xyz',
      tokenType: 'Bearer',
      expiresIn: 900,
    };

    service.refreshToken().subscribe((res) => {
      expect(res.accessToken).toBe('new-rotated-token-xyz');
    });

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    req.flush(refreshResponse);

    // Expect fallback call to /me
    const meReq = httpTesting.expectOne('/api/v1/auth/me');
    expect(meReq.request.headers.get('Authorization')).toBe('Bearer new-rotated-token-xyz');
    meReq.flush(mockUser);

    expect(service.accessToken()).toBe('new-rotated-token-xyz');
    expect(service.currentUser()).toEqual(mockUser);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('refreshToken failure should clear in-memory auth state', () => {
    service.setSession('stale-token', mockUser);

    service.refreshToken().subscribe({
      error: (err) => {
        expect(err.status).toBe(401);
      },
    });

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(service.accessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('concurrent refreshToken calls should share single in-flight HTTP request', () => {
    let res1: TokenRefreshResponse | undefined;
    let res2: TokenRefreshResponse | undefined;

    // Trigger two refreshes simultaneously
    service.refreshToken().subscribe((r) => (res1 = r));
    service.refreshToken().subscribe((r) => (res2 = r));

    // Exactly one HTTP request should be dispatched
    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    req.flush({
      accessToken: 'single-flight-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    });

    expect(res1?.accessToken).toBe('single-flight-token');
    expect(res2?.accessToken).toBe('single-flight-token');
  });

  it('initializeSession should restore user session on startup', async () => {
    const initPromise = service.initializeSession();

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    req.flush({
      accessToken: 'startup-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    });

    const result = await initPromise;
    expect(result).toBe(true);
    expect(service.isInitialized()).toBe(true);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('startup-token');
    expect(service.currentUser()).toEqual(mockUser);
  });

  it('initializeSession should resolve false and set isInitialized on 401 without throwing', async () => {
    const initPromise = service.initializeSession();

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    req.flush('No cookie', { status: 401, statusText: 'Unauthorized' });

    const result = await initPromise;
    expect(result).toBe(false);
    expect(service.isInitialized()).toBe(true);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken()).toBeNull();
  });

  it('account switching should completely replace previous user session and role', () => {
    // 1. Initial login as Customer
    service.setSession('token-cust', mockUser);
    expect(service.userRole()).toBe('CUSTOMER');

    // 2. Logout
    service.clearAuth();
    expect(service.accessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();

    // 3. New login as Merchant
    service.setSession('token-mch', mockMerchantUser);
    expect(service.accessToken()).toBe('token-mch');
    expect(service.currentUser()?.email).toBe('merchant@daraz.lk');
    expect(service.userRole()).toBe('MERCHANT');
    expect(service.hasRole('CUSTOMER')).toBe(false);
    expect(service.hasRole('MERCHANT')).toBe(true);
  });

  it('logout should call backend endpoint and clear in-memory credentials', () => {
    service.setSession('active-token', mockUser);

    service.logout().subscribe();

    const req = httpTesting.expectOne('/api/v1/auth/logout');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush(null);

    expect(service.accessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('forgotPassword should post to /api/v1/auth/password/forgot', () => {
    const responsePayload = {
      message: 'If an account exists with this email, password reset instructions have been sent.',
    };

    service.forgotPassword({ email: 'user@example.com' }).subscribe((res) => {
      expect(res.message).toBe(responsePayload.message);
    });

    const req = httpTesting.expectOne('/api/v1/auth/password/forgot');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'user@example.com' });
    req.flush(responsePayload);
  });

  it('resetPassword should post to /api/v1/auth/password/reset', () => {
    const responsePayload = {
      message: 'Password has been successfully reset. Please log in with your new password.',
    };

    service.resetPassword({ token: 'tok-123', newPassword: 'NewP@ssword1!' }).subscribe((res) => {
      expect(res.message).toBe(responsePayload.message);
    });

    const req = httpTesting.expectOne('/api/v1/auth/password/reset');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok-123', newPassword: 'NewP@ssword1!' });
    req.flush(responsePayload);
  });

  it('loginWithGoogle should navigate to /api/v1/auth/oauth2/google', () => {
    const navSpy = jest.spyOn(service as any, 'navigateTo').mockImplementation(() => {});
    service.loginWithGoogle();
    expect(navSpy).toHaveBeenCalledWith('/api/v1/auth/oauth2/google');
  });
});
