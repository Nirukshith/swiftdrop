import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let authServiceMock: jest.Mocked<Partial<AuthService>>;
  let routerMock: jest.Mocked<Partial<Router>>;

  beforeEach(() => {
    authServiceMock = {
      accessToken: jest.fn().mockReturnValue('initial-token-123'),
      refreshToken: jest.fn().mockReturnValue(
        of({ accessToken: 'refreshed-token-456', tokenType: 'Bearer', expiresIn: 900 })
      ),
      clearAuth: jest.fn(),
    };

    routerMock = {
      navigate: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should attach Authorization Bearer header when token is in memory', () => {
    http.get('/api/v1/parcels/123').subscribe();

    const req = httpTesting.expectOne('/api/v1/parcels/123');
    expect(req.request.headers.get('Authorization')).toBe('Bearer initial-token-123');
    req.flush({});
  });

  it('should set withCredentials: true for auth endpoints', () => {
    http.post('/api/v1/auth/login', {}).subscribe();

    const req = httpTesting.expectOne('/api/v1/auth/login');
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });

  it('should refresh token on 401 error and retry original request with new token', () => {
    http.get('/api/v1/parcels/test-401').subscribe((res) => {
      expect(res).toEqual({ success: true });
    });

    // Initial request fails with 401
    const req1 = httpTesting.expectOne('/api/v1/parcels/test-401');
    expect(req1.request.headers.get('Authorization')).toBe('Bearer initial-token-123');
    req1.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    // Verify refreshToken was called
    expect(authServiceMock.refreshToken).toHaveBeenCalledTimes(1);

    // Retried request should have new token
    const retryReq = httpTesting.expectOne('/api/v1/parcels/test-401');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer refreshed-token-456');
    retryReq.flush({ success: true });
  });

  it('should clear auth and navigate to /login when refresh fails on 401', () => {
    (authServiceMock.refreshToken as jest.Mock).mockReturnValue(
      throwError(() => new Error('Refresh failed'))
    );

    http.get('/api/v1/parcels/test-fail').subscribe({
      error: () => {
        expect(authServiceMock.clearAuth).toHaveBeenCalled();
        expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
      },
    });

    const req = httpTesting.expectOne('/api/v1/parcels/test-fail');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
  });
});
