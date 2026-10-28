import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard, roleGuard, dataRoleGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('Auth Guards', () => {
  let authServiceMock: {
    isAuthenticated: jest.Mock;
    hasAnyRole: jest.Mock;
    userRole: jest.Mock;
  };
  let router: Router;

  const dummyRoute = {} as ActivatedRouteSnapshot;
  const dummyState = { url: '/merchant/dashboard' } as RouterStateSnapshot;

  beforeEach(() => {
    authServiceMock = {
      isAuthenticated: jest.fn(),
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
    it('should allow access when user is authenticated', () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);

      const result = TestBed.runInInjectionContext(() => authGuard(dummyRoute, dummyState));
      expect(result).toBe(true);
    });

    it('should redirect to /login with returnUrl query param when user is not authenticated', () => {
      authServiceMock.isAuthenticated.mockReturnValue(false);

      const result = TestBed.runInInjectionContext(() => authGuard(dummyRoute, dummyState));
      expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/merchant/dashboard' },
      });
      expect(result).toBeTruthy();
    });
  });

  describe('roleGuard', () => {
    it('should redirect to /login when user is not authenticated', () => {
      authServiceMock.isAuthenticated.mockReturnValue(false);

      const guard = roleGuard(['ADMIN', 'MERCHANT']);
      const result = TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/merchant/dashboard' },
      });
      expect(result).toBeTruthy();
    });

    it('should allow access when authenticated user has matching role', () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(true);

      const guard = roleGuard(['ADMIN', 'MERCHANT']);
      const result = TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(result).toBe(true);
      expect(authServiceMock.hasAnyRole).toHaveBeenCalledWith(['ADMIN', 'MERCHANT']);
    });

    it('should redirect to user role dashboard when user role does not match', () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(false);
      authServiceMock.userRole.mockReturnValue('CUSTOMER');

      const guard = roleGuard(['ADMIN']);
      const result = TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/customer']);
      expect(result).toBeTruthy();
    });

    it('should redirect to /login when unauthorized user has no role defined', () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(false);
      authServiceMock.userRole.mockReturnValue(undefined);

      const guard = roleGuard(['ADMIN']);
      const result = TestBed.runInInjectionContext(() => guard(dummyRoute, dummyState));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
      expect(result).toBeTruthy();
    });
  });

  describe('dataRoleGuard', () => {
    it('should delegate to authGuard when route.data has no roles', () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      const routeWithoutRoles = { data: {} } as ActivatedRouteSnapshot;

      const result = TestBed.runInInjectionContext(() => dataRoleGuard(routeWithoutRoles, dummyState));
      expect(result).toBe(true);
    });

    it('should enforce roles when specified in route.data', () => {
      authServiceMock.isAuthenticated.mockReturnValue(true);
      authServiceMock.hasAnyRole.mockReturnValue(true);

      const routeWithRoles = { data: { roles: ['ADMIN'] } } as unknown as ActivatedRouteSnapshot;

      const result = TestBed.runInInjectionContext(() => dataRoleGuard(routeWithRoles, dummyState));
      expect(result).toBe(true);
      expect(authServiceMock.hasAnyRole).toHaveBeenCalledWith(['ADMIN']);
    });
  });
});
