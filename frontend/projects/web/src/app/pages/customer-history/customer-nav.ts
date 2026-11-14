import { HttpErrorResponse } from '@angular/common/http';
import { ShellNavItem } from 'ui';

/** Navigation shared by the customer pages. `/customer` is M1's delivery wizard. */
export const CUSTOMER_NAV: ShellNavItem[] = [
  { label: 'Dashboard', link: '/customer/dashboard' },
  { label: 'My parcels', link: '/customer/parcels' },
  { label: 'New delivery', link: '/customer', exact: true },
];

/** Readable message from a failed API call (uses the shared ErrorResponse when present). */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { message?: unknown } | null;
    if (body && typeof body.message === 'string' && body.message) {
      return body.message;
    }
    if (error.status === 0) {
      return 'Cannot reach the server. Check your connection and try again.';
    }
  }
  return 'Please try again in a moment.';
}
