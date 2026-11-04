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
    hasAnyRole: jest.Mock;
    userRole: jest.Mock;
  };

  beforeEach(() => {
    let currentRole: string | null = null;
    authServiceMock = {
      isAuthenticated: jest.fn().mockReturnValue(true),
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

  it('allows ADMIN to access /admin/users', async () => {
    (authServiceMock as any).setMockRole('ADMIN');

    const canNavigate = await router.navigateByUrl('/admin/users');
    expect(canNavigate).toBe(true);
    expect(router.url).toBe('/admin/users');
  });

  it('redirects DISPATCHER away from /admin/users to /dispatcher (which redirects to /dispatch)', async () => {
    (authServiceMock as any).setMockRole('DISPATCHER');

    await router.navigateByUrl('/admin/users');
    expect(router.url).toBe('/dispatch');
  });
});
