import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, of, startWith, switchMap, tap } from 'rxjs';
import { LiveTimeline } from './live-timeline';
import { StreamState, TrackingStreamService } from './tracking-stream.service';
import { ParcelStatus, TERMINAL_STATUSES } from './tracking.model';

/** What a tracking page shows instead of the timeline. */
export type PageState = 'loading' | 'ready' | 'not-found' | 'forbidden' | 'unauthorized' | 'error';

/**
 * Feeds a LiveTimeline from the stream of one parcel. A finished parcel (DELIVERED, CANCELLED)
 * gets no connection. `retry()` opens a new connection after the browser gave up.
 */
export function followParcel(
  streams: TrackingStreamService,
  timeline: LiveTimeline,
  trackingNumber: string,
  status: ParcelStatus,
  state: (s: StreamState) => void,
  retry$: Subject<void>,
): Observable<unknown> {
  if (TERMINAL_STATUSES.includes(status)) {
    state('ended');
    return of(null);
  }
  return retry$.pipe(
    startWith(undefined),
    switchMap(() => streams.connect(trackingNumber)),
    tap((message) => {
      if (message.type === 'state') {
        state(message.state);
      } else {
        timeline.apply(message.event);
      }
    }),
  );
}

/** Maps a failed REST load to a page state and a message. */
export function failure(error: unknown): { state: PageState; message: string } {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { message?: unknown } | null;
    const apiMessage = body && typeof body.message === 'string' ? body.message : '';
    switch (error.status) {
      case 401:
        return { state: 'unauthorized', message: 'Your session has ended. Please sign in again.' };
      case 403:
        return { state: 'forbidden', message: 'You do not have access to this parcel.' };
      case 404:
        return { state: 'not-found', message: apiMessage || 'No parcel found.' };
      case 0:
        return { state: 'error', message: 'Cannot reach the server. Check your connection and try again.' };
      default:
        return { state: 'error', message: apiMessage || 'Please try again in a moment.' };
    }
  }
  return { state: 'error', message: 'Please try again in a moment.' };
}
