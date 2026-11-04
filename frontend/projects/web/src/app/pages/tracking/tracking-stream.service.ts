import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PARCEL_STATUSES, PublicTrackingEvent, TERMINAL_STATUSES } from './tracking.model';

/** Connection state shown by the live indicator. */
export type StreamState = 'connecting' | 'live' | 'reconnecting' | 'disconnected' | 'ended';

export type StreamMessage =
  | { type: 'state'; state: StreamState }
  | { type: 'event'; event: PublicTrackingEvent };

/** Minimal part of EventSource used here; replaced by a fake in unit tests. */
export interface EventSourceLike {
  readonly readyState: number;
  onopen: ((event: Event) => unknown) | null;
  onerror: ((event: Event) => unknown) | null;
  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void;
  close(): void;
}

export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSourceLike>('EVENT_SOURCE_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

/** EventSource.readyState values. */
const CONNECTING = 0;
const CLOSED = 2;

/**
 * Public live tracking stream (parcel-read.yaml `/api/v1/tracking/{trackingNo}/stream`).
 *
 * - One EventSource per subscription; unsubscribing closes it, so leaving the page, switching
 *   parcels or logging out never leaves a connection or a reconnect loop behind.
 * - The browser reconnects by itself after `retry` and sends `Last-Event-ID`; the server then
 *   replays only the missed changes.
 * - Only well-formed `status` events for the requested tracking number are passed on.
 * - After DELIVERED or CANCELLED the stream is closed here, so the browser does not reconnect
 *   to a finished parcel.
 */
@Injectable({ providedIn: 'root' })
export class TrackingStreamService {
  private readonly createEventSource = inject(EVENT_SOURCE_FACTORY);

  static url(trackingNumber: string): string {
    return `/api/v1/tracking/${encodeURIComponent(trackingNumber)}/stream`;
  }

  connect(trackingNumber: string): Observable<StreamMessage> {
    return new Observable<StreamMessage>((subscriber) => {
      const source = this.createEventSource(TrackingStreamService.url(trackingNumber));
      let finished = false;
      const finish = (state: StreamState) => {
        if (!finished) {
          finished = true;
          source.close();
          subscriber.next({ type: 'state', state });
          subscriber.complete();
        }
      };

      subscriber.next({ type: 'state', state: 'connecting' });
      source.onopen = () => subscriber.next({ type: 'state', state: 'live' });
      source.onerror = () => {
        if (source.readyState === CLOSED) {
          // Not reconnecting any more (e.g. 404, or the server refused the stream).
          finish('disconnected');
        } else if (source.readyState === CONNECTING) {
          subscriber.next({ type: 'state', state: 'reconnecting' });
        }
      };
      source.addEventListener('status', (message) => {
        const event = parseEvent(message.data, trackingNumber);
        if (!event) {
          return;
        }
        subscriber.next({ type: 'event', event });
        if (TERMINAL_STATUSES.includes(event.status)) {
          finish('ended');
        }
      });

      return () => {
        finished = true;
        source.close();
      };
    });
  }
}

/** Parses and checks an event body; anything malformed or for another parcel is dropped. */
export function parseEvent(data: string, trackingNumber: string): PublicTrackingEvent | null {
  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch {
    return null;
  }
  if (!value || typeof value !== 'object') {
    return null;
  }
  const e = value as Record<string, unknown>;
  const valid =
    e['trackingNumber'] === trackingNumber &&
    typeof e['sequence'] === 'number' &&
    Number.isInteger(e['sequence']) &&
    (e['sequence'] as number) > 0 &&
    typeof e['status'] === 'string' &&
    (PARCEL_STATUSES as readonly string[]).includes(e['status']) &&
    typeof e['occurredAt'] === 'string' &&
    !Number.isNaN(Date.parse(e['occurredAt']));
  if (!valid) {
    return null;
  }
  return {
    trackingNumber,
    sequence: e['sequence'] as number,
    status: e['status'] as PublicTrackingEvent['status'],
    occurredAt: e['occurredAt'] as string,
  };
}
