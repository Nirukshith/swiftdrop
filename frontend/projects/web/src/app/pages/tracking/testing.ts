import { EVENT_SOURCE_FACTORY, EventSourceLike } from './tracking-stream.service';
import { PublicTrackingEvent } from './tracking.model';

/** EventSource stand-in that tests drive by hand. */
export class FakeEventSource implements EventSourceLike {
  static instances: FakeEventSource[] = [];

  readyState = 0;
  closed = false;
  onopen: ((event: Event) => unknown) | null = null;
  onerror: ((event: Event) => unknown) | null = null;
  private readonly listeners = new Map<string, ((event: MessageEvent<string>) => void)[]>();

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  static reset(): void {
    FakeEventSource.instances = [];
  }

  static latest(): FakeEventSource {
    const latest = FakeEventSource.instances.at(-1);
    if (!latest) {
      throw new Error('No EventSource was opened');
    }
    return latest;
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
    this.readyState = 2;
  }

  /** The server accepted the connection. */
  open(): void {
    this.readyState = 1;
    this.onopen?.(new Event('open'));
  }

  /** The server sent an event; objects are sent as JSON. */
  emit(data: PublicTrackingEvent | string, type = 'status'): void {
    const payload = typeof data === 'string' ? data : JSON.stringify(data);
    this.listeners.get(type)?.forEach((listener) => listener({ data: payload } as MessageEvent<string>));
  }

  /** Connection lost: 0 = the browser will reconnect, 2 = it gave up. */
  fail(readyState: 0 | 2): void {
    this.readyState = readyState;
    this.onerror?.(new Event('error'));
  }
}

export const fakeEventSourceProvider = {
  provide: EVENT_SOURCE_FACTORY,
  useValue: (url: string) => new FakeEventSource(url),
};

export function statusEvent(
  trackingNumber: string,
  sequence: number,
  status: PublicTrackingEvent['status'],
  occurredAt = `2026-11-04T1${sequence}:00:00Z`,
): PublicTrackingEvent {
  return { trackingNumber, sequence, status, occurredAt };
}
