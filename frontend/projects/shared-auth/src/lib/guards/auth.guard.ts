import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Ensures user is authenticated before allowing route activation.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url },
  });
};

/**
 * Restricts route access to users with specified role(s).
 *
 * @param allowedRoles List of roles permitted to access the route
 */
export const roleGuard = (allowedRoles: string[]): CanActivateFn => {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url },
      });
    }

    if (authService.hasAnyRole(allowedRoles)) {
      return true;
    }

    // Role unauthorized - redirect to appropriate dashboard or home
    const userRole = authService.userRole()?.toLowerCase();
    const fallbackPath = userRole ? `/${userRole}` : '/login';
    return router.createUrlTree([fallbackPath]);
  };
};

/**
 * Route guard that inspects `route.data['roles']` to authorize access.
 */
export const dataRoleGuard: CanActivateFn = (route, state) => {
  const allowedRoles = (route.data['roles'] as string[]) || [];
  if (allowedRoles.length === 0) {
    return authGuard(route, state);
  }
  return roleGuard(allowedRoles)(route, state);
};
