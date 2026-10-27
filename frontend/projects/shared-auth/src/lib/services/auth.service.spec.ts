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

  it('should initialize with no in-memory token and unauthenticated', () => {
    expect(service.accessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('login should store access token in memory only and set currentUser', () => {
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
  });

  it('refreshToken should rotate in-memory access token', () => {
    // Initial login
    service.setSession('old-token', mockUser);

    const refreshResponse: TokenRefreshResponse = {
      accessToken: 'new-rotated-token-xyz',
      tokenType: 'Bearer',
      expiresIn: 900,
    };

    service.refreshToken().subscribe((res) => {
      expect(res).toEqual(refreshResponse);
    });

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush(refreshResponse);

    expect(service.accessToken()).toBe('new-rotated-token-xyz');
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
});
