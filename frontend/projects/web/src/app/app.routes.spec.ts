import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Location } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AuthService } from 'shared-auth';
import { routes } from './app.routes';

describe('App Route Role Separation', () => {
  let router: Router;
  let authServiceMock: {
    isAuthenticated: jest.Mock;
    isInitialized: jest.Mock;
    initializeSession: jest.Mock;
    hasAnyRole: jest.Mock;
    userRole: jest.Mock;
  };

  beforeEach(() => {
    let currentRole: string | null = null;
    authServiceMock = {
      isAuthenticated: jest.fn().mockReturnValue(true),
      isInitialized: jest.fn().mockReturnValue(true),
      initializeSession: jest.fn().mockResolvedValue(true),
      hasAnyRole: jest.fn().mockImplementation((roles: string[]) =>
        currentRole ? roles.includes(currentRole) : false
      ),
      userRole: jest.fn().mockImplementation(() => currentRole),
    };

    (authServiceMock as any).setMockRole = (role: string | null) => {
      currentRole = role;
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: AuthService, useValue: authServiceMock },
      ],
    });

    router = TestBed.inject(Router);
  });

  it('allows DISPATCHER to access /dispatch', async () => {
    (authServiceMock as any).setMockRole('DISPATCHER');

    const canNavigate = await router.navigateByUrl('/dispatch');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/dispatch');
  });

  it('redirects ADMIN away from /dispatch to /admin', async () => {
    (authServiceMock as any).setMockRole('ADMIN');

    await router.navigateByUrl('/dispatch');
    expect(router.url).toBe('/admin');
  });

  it('allows DISPATCHER to access /dispatch/zones', async () => {
    (authServiceMock as any).setMockRole('DISPATCHER');

    const canNavigate = await router.navigateByUrl('/dispatch/zones');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/dispatch/zones');
  });

  it('redirects ADMIN away from /dispatch/zones to /admin', async () => {
    (authServiceMock as any).setMockRole('ADMIN');

    await router.navigateByUrl('/dispatch/zones');
    expect(router.url).toBe('/admin');
  });

  it('allows DISPATCHER to access /dispatch/pricing', async () => {
    (authServiceMock as any).setMockRole('DISPATCHER');

    const canNavigate = await router.navigateByUrl('/dispatch/pricing');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/dispatch/pricing');
  });

  it('redirects ADMIN away from /dispatch/pricing to /admin', async () => {
    (authServiceMock as any).setMockRole('ADMIN');

    await router.navigateByUrl('/dispatch/pricing');
    expect(router.url).toBe('/admin');
  });

  it('allows ADMIN to access /admin/users', async () => {
    (authServiceMock as any).setMockRole('ADMIN');

    const canNavigate = await router.navigateByUrl('/admin/users');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/admin/users');
  });

  it('allows ADMIN to access /admin/merchants', async () => {
    (authServiceMock as any).setMockRole('ADMIN');

    const canNavigate = await router.navigateByUrl('/admin/merchants');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/admin/merchants');
  });

  it.each(['DISPATCHER', 'CUSTOMER', 'MERCHANT', 'COURIER'])(
    'redirects %s away from /admin/merchants',
    async (role) => {
      (authServiceMock as any).setMockRole(role);

      await router.navigateByUrl('/admin/merchants');

      expect(router.url).not.toBe('/admin/merchants');
    }
  );

  it('redirects DISPATCHER away from /admin/users to /dispatcher (which redirects to /dispatch)', async () => {
    (authServiceMock as any).setMockRole('DISPATCHER');

    await router.navigateByUrl('/admin/users');
    expect(router.url).toBe('/dispatch');
  });

  it('allows public access to /forgot-password', async () => {
    (authServiceMock as any).setMockRole(null);

    const canNavigate = await router.navigateByUrl('/forgot-password');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/forgot-password');
  });

  it('allows public access to /reset-password with query parameter', async () => {
    (authServiceMock as any).setMockRole(null);

    const canNavigate = await router.navigateByUrl('/reset-password?token=test-token');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/reset-password?token=test-token');
  });
});
