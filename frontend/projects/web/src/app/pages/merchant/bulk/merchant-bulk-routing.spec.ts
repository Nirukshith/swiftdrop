import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from 'shared-auth';
import { routes } from '../../../app.routes';

@Component({ standalone: true, template: 'Fallback page' })
class FallbackComponent {}

describe('Bulk merchant route authorization', () => {
  let authenticated: boolean, role: string;
  beforeEach(() => {
    authenticated = true; role = 'MERCHANT';
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      provideRouter([
        routes.find(route => route.path === 'merchant/bulk')!,
        { path: '', component: FallbackComponent },
        { path: 'login', component: FallbackComponent },
        { path: 'customer', component: FallbackComponent },
      ]),
      { provide: AuthService, useValue: {
        isInitialized: () => true, isAuthenticated: () => authenticated,
        hasAnyRole: (roles: string[]) => roles.includes(role), userRole: () => role,
      } },
    ] });
  });
  it('loads the actual lazy bulk page for a merchant', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/merchant/bulk');
    expect(TestBed.inject(Router).url).toBe('/merchant/bulk');
    expect(harness.routeNativeElement?.textContent).toContain('Bulk CSV orders');
  });
  it('redirects unauthenticated users to login with the return URL', async () => {
    authenticated = false;
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/merchant/bulk');
    expect(TestBed.inject(Router).url).toContain('/login?returnUrl=');
    expect(harness.routeNativeElement?.textContent).not.toContain('Bulk CSV orders');
  });
  it('redirects a customer away from the merchant upload', async () => {
    role = 'CUSTOMER';
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/merchant/bulk');
    expect(TestBed.inject(Router).url).toBe('/customer');
  });
});
