import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Saves a downloaded file in the browser; replaced by a fake in unit tests. */
export const SAVE_FILE = new InjectionToken<(blob: Blob, fileName: string) => void>('SAVE_FILE', {
  providedIn: 'root',
  factory: () => saveWithLink,
});

/**
 * PDF receipt of a delivered parcel (parcel-read.yaml `GET /api/v1/parcels/{id}/receipt`).
 * The bearer token is added by the shared-auth interceptor; the server checks that the caller
 * owns the parcel and that it is delivered.
 */
@Injectable({ providedIn: 'root' })
export class ReceiptService {
  private readonly http = inject(HttpClient);
  private readonly save = inject(SAVE_FILE);

  static url(parcelId: string): string {
    return `/api/v1/parcels/${encodeURIComponent(parcelId)}/receipt`;
  }

  /** Downloads the receipt and hands it to the browser as a file. Emits once, then completes. */
  download(parcelId: string): Observable<string> {
    return this.http
      .get(ReceiptService.url(parcelId), { observe: 'response', responseType: 'blob', headers: { Accept: 'application/pdf' } })
      .pipe(
        map((response: HttpResponse<Blob>) => {
          const fileName = fileNameFrom(response.headers.get('Content-Disposition'));
          this.save(response.body ?? new Blob([], { type: 'application/pdf' }), fileName);
          return fileName;
        }),
      );
  }
}

/** File name from `Content-Disposition: attachment; filename="receipt-SD-....pdf"`. */
export function fileNameFrom(header: string | null): string {
  const match = header ? /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header) : null;
  const name = match ? decodeURIComponent(match[1].trim()) : '';
  // Keep only a plain file name; never a path.
  return /^[\w.-]+\.pdf$/i.test(name) ? name : 'receipt.pdf';
}

/** User-facing message for a failed download (the error body is a Blob, so codes are mapped here). */
export function receiptErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    switch (error.status) {
      case 0:
        return 'Cannot reach the server. Check your connection and try again.';
      case 401:
        return 'Your session has ended. Please sign in again.';
      case 403:
        return 'You do not have access to this receipt.';
      case 404:
        return 'This parcel could not be found.';
      case 409:
        return 'The receipt is available once the parcel has been delivered.';
    }
  }
  return 'The receipt could not be downloaded. Please try again.';
}

function saveWithLink(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.rel = 'noopener';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
