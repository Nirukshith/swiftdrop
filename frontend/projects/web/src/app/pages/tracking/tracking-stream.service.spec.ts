import { TestBed } from '@angular/core/testing';
import { StreamMessage, TrackingStreamService, parseEvent } from './tracking-stream.service';
import { FakeEventSource, fakeEventSourceProvider, statusEvent } from './testing';

const TN = 'SD-20261104-A1B2C3';

describe('TrackingStreamService', () => {
  let service: TrackingStreamService;
  let messages: StreamMessage[];

  beforeEach(() => {
    FakeEventSource.reset();
    TestBed.configureTestingModule({ providers: [fakeEventSourceProvider] });
    service = TestBed.inject(TrackingStreamService);
    messages = [];
  });

  const states = () => messages.filter((m) => m.type === 'state').map((m) => (m.type === 'state' ? m.state : null));
  const events = () => messages.filter((m) => m.type === 'event').map((m) => (m.type === 'event' ? m.event : null));

  it('opens one EventSource on the contract URL for the requested parcel', () => {
    service.connect(TN).subscribe((m) => messages.push(m));

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.latest().url).toBe(`/api/v1/tracking/${TN}/stream`);
    expect(states()).toEqual(['connecting']);

    FakeEventSource.latest().open();
    expect(states()).toEqual(['connecting', 'live']);
  });

  it('passes on valid status events and drops malformed ones or ones for another parcel', () => {
    service.connect(TN).subscribe((m) => messages.push(m));
    const source = FakeEventSource.latest();

    source.emit(statusEvent(TN, 4, 'IN_TRANSIT'));
    source.emit('{not json');
    source.emit(statusEvent('SD-20261104-OTHER1', 5, 'DELIVERED'));
    source.emit(JSON.stringify({ trackingNumber: TN, sequence: 'x', status: 'IN_TRANSIT', occurredAt: '2026-11-04T10:00:00Z' }));
    source.emit(JSON.stringify({ trackingNumber: TN, sequence: 6, status: 'LOST', occurredAt: '2026-11-04T10:00:00Z' }));
    source.emit(statusEvent(TN, 7, 'OUT_FOR_DELIVERY'), 'message'); // not the contract's event name

    expect(events()).toEqual([statusEvent(TN, 4, 'IN_TRANSIT')]);
  });

  it('reports reconnecting while the browser retries, and disconnected when it gives up', () => {
    service.connect(TN).subscribe((m) => messages.push(m));
    const source = FakeEventSource.latest();
    source.open();

    source.fail(0);
    expect(states().at(-1)).toBe('reconnecting');
    source.open();
    expect(states().at(-1)).toBe('live');

    source.fail(2);
    expect(states().at(-1)).toBe('disconnected');
    expect(source.closed).toBe(true);
  });

  it('closes the connection after a terminal status so the browser does not reconnect', () => {
    let completed = false;
    service.connect(TN).subscribe({ next: (m) => messages.push(m), complete: () => (completed = true) });
    const source = FakeEventSource.latest();

    source.emit(statusEvent(TN, 6, 'DELIVERED'));

    expect(events()).toHaveLength(1);
    expect(states().at(-1)).toBe('ended');
    expect(source.closed).toBe(true);
    expect(completed).toBe(true);
  });

  it('closes the EventSource when unsubscribed', () => {
    const subscription = service.connect(TN).subscribe((m) => messages.push(m));
    const source = FakeEventSource.latest();

    subscription.unsubscribe();

    expect(source.closed).toBe(true);
  });

  it('parseEvent only accepts complete events for the given parcel', () => {
    expect(parseEvent(JSON.stringify(statusEvent(TN, 1, 'CREATED')), TN)).toEqual(statusEvent(TN, 1, 'CREATED'));
    expect(parseEvent('null', TN)).toBeNull();
    expect(parseEvent(JSON.stringify({ ...statusEvent(TN, 1, 'CREATED'), occurredAt: 'yesterday' }), TN)).toBeNull();
    expect(parseEvent(JSON.stringify({ ...statusEvent(TN, 0, 'CREATED') }), TN)).toBeNull();
  });
});
