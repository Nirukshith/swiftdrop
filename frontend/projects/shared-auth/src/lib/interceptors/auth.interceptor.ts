import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * HTTP interceptor for SwiftDrop API requests.
 *
 * Capabilities:
 * 1. Attaches the in-memory access token to outgoing requests (Bearer <token>).
 * 2. Ensures withCredentials: true on auth endpoints for httpOnly refresh cookie transit.
 * 3. On 401 Unauthorized, automatically triggers a silent refresh ONCE and retries the original request.
 * 4. If refresh fails, clears in-memory credentials and redirects to login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.accessToken();

  let authReq = req;

  // Ensure withCredentials: true for authentication endpoints
  if (req.url.includes('/api/v1/auth')) {
    authReq = authReq.clone({ withCredentials: true });
  }

  // Attach in-memory access token if present
  if (token && !req.headers.has('Authorization')) {
    authReq = authReq.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Handle 401 Unauthorized by attempting a single silent refresh
      if (
        error.status === 401 &&
        !req.url.includes('/api/v1/auth/login') &&
        !req.url.includes('/api/v1/auth/register') &&
        !req.url.includes('/api/v1/auth/refresh')
      ) {
        return authService.refreshToken().pipe(
          switchMap((refreshRes) => {
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${refreshRes.accessToken}`,
              },
            });
            return next(retryReq);
          }),
          catchError((refreshErr) => {
            authService.clearAuth();
            router.navigate(['/login']);
            return throwError(() => refreshErr);
          })
        );
      }

      return throwError(() => error);
    })
  );
};
