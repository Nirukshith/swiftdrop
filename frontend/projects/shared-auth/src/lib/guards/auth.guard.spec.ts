import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard, roleGuard, dataRoleGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('Auth Guards', () => {
  let authServiceMock: {
    isAuthenticated: jest.Mock;
    isInitialized: jest.Mock;
    initializeSession: jest.Mock;
    hasAnyRole: jest.Mock;
    userRole: jest.Mock;
  };
  let router: Router;

  const dummyRoute = {} as ActivatedRouteSnapshot;
  const dummyState = { url: '/merchant/dashboard' } as RouterStateSnapshot;

  beforeEach(() => {
    authServiceMock = {
      isAuthenticated: jest.fn(),
      isInitialized: jest.fn().mockReturnValue(true),
      initializeSession: jest.fn().mockResolvedValue(true),
      hasAnyRole: jest.fn(),
      userRole: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
      ],
    });

    router = TestBed.inject(Router);
    jest.spyOn(router, 'createUrlTree').mockImplementation((commands: any[], navigationExtras?: any) => {
      return { toString: () => commands.join('/'), commands, navigationExtras } as unknown as UrlTree;
    });
  });

  describe('authGuard', () => {
    it('should allow access when user is authenticated', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);

      const result = await TestBed.runInInjectionContext(() => authGuard(dummyRoute, dummyState));
      expect(result).toBe(true);
    });

    it('should wait for initializeSession if not initialized before checking auth', async () => {
      authServiceMock.isInitialized.mockReturnValue(false);
      authServiceMock.initializeSession.mockImplementation(async () => {
        authServiceMock.isInitialized.mockReturnValue(true);
        authServiceMock.isAuthenticated.mockReturnValue(true);
        return true;
      });

      const result = await TestBed.runInInjectionContext(() => authGuard(dummyRoute, dummyState));
      expect(authServiceMock.initializeSession).toHaveBeenCalled();
      expect(result).toBe(true);
    });

    it('should redirect to /login with returnUrl query param when user is not authenticated', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(false);

      const result = await TestBed.runInInjectionContext(() => authGuard(dummyRoute, dummyState));
      expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/merchant/dashboard' },
      });
      expect(result).toBeTruthy();
    });
  });

  describe('roleGuard', () => {
    it('should redirect to /login when user is not authenticated', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(false);

      const guard = roleGuard(['ADMIN', 'MERCHANT']);
      const result = await TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/merchant/dashboard' },
      });
      expect(result).toBeTruthy();
    });

    it('should restore session and allow access when valid session exists on reload', async () => {
      authServiceMock.isInitialized.mockReturnValue(false);
      authServiceMock.initializeSession.mockImplementation(async () => {
        authServiceMock.isInitialized.mockReturnValue(true);
        authServiceMock.isAuthenticated.mockReturnValue(true);
        authServiceMock.hasAnyRole.mockReturnValue(true);
        authServiceMock.userRole.mockReturnValue('MERCHANT');
        return true;
      });

      const guard = roleGuard(['MERCHANT']);
      const result = await TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(authServiceMock.initializeSession).toHaveBeenCalled();
      expect(result).toBe(true);
    });

    it('should allow access when authenticated user has matching role', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(true);

      const guard = roleGuard(['ADMIN', 'MERCHANT']);
      const result = await TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(result).toBe(true);
      expect(authServiceMock.hasAnyRole).toHaveBeenCalledWith(['ADMIN', 'MERCHANT']);
    });

    it('should redirect to user role dashboard when user role does not match', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(false);
      authServiceMock.userRole.mockReturnValue('CUSTOMER');

      const guard = roleGuard(['ADMIN']);
      const result = await TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/customer']);
      expect(result).toBeTruthy();
    });

    it('should redirect to /login when unauthorized user has no role defined', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(false);
      authServiceMock.userRole.mockReturnValue(undefined);

      const guard = roleGuard(['ADMIN']);
      const result = await TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
      expect(result).toBeTruthy();
    });
  });

  describe('dataRoleGuard', () => {
    it('should fall back to authGuard when route.data has no roles', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);

      const emptyRoute = { data: {} } as ActivatedRouteSnapshot;
      const result = await TestBed.runInInjectionContext(() => dataRoleGuard(emptyRoute, dummyState));

      expect(result).toBe(true);
    });

    it('should enforce role when route.data specifies roles', async () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(false);
      authServiceMock.userRole.mockReturnValue('COURIER');

      const adminRoute = { data: { roles: ['ADMIN'] } } as unknown as ActivatedRouteSnapshot;
      const result = await TestBed.runInInjectionContext(() => dataRoleGuard(adminRoute, dummyState));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/courier']);
      expect(result).toBeTruthy();
    });
  });
});
