import { LiveTimeline } from './live-timeline';
import { statusEvent } from './testing';

const TN = 'SD-20261104-A1B2C3';

describe('LiveTimeline', () => {
  let timeline: LiveTimeline;

  beforeEach(() => {
    timeline = new LiveTimeline();
    timeline.reset(
      TN,
      'PICKED_UP',
      [
        { status: 'PICKED_UP', occurredAt: '2026-11-04T09:00:00Z' },
        { status: 'CREATED', occurredAt: '2026-11-04T08:00:00Z' },
      ],
      '2026-11-04T09:00:00Z',
    );
  });

  it('orders the loaded history by time', () => {
    expect(timeline.items().map((i) => i.status)).toEqual(['CREATED', 'PICKED_UP']);
    expect(timeline.status()).toBe('PICKED_UP');
  });

  it('the state sent at connect repeats the current status and adds no row', () => {
    expect(timeline.apply(statusEvent(TN, 3, 'PICKED_UP'))).toBe(false);
    expect(timeline.items()).toHaveLength(2);
  });

  it('adds a row and updates status and time for a new status', () => {
    timeline.apply(statusEvent(TN, 3, 'PICKED_UP'));
    expect(timeline.apply(statusEvent(TN, 4, 'IN_TRANSIT', '2026-11-04T10:00:00Z'))).toBe(true);

    expect(timeline.items().map((i) => i.status)).toEqual(['CREATED', 'PICKED_UP', 'IN_TRANSIT']);
    expect(timeline.status()).toBe('IN_TRANSIT');
    expect(timeline.lastUpdatedAt()).toBe('2026-11-04T10:00:00Z');
    expect(timeline.lastUpdateLive()).toBe(true);
  });

  it('ignores duplicates and older sequences (replay after reconnect)', () => {
    timeline.apply(statusEvent(TN, 4, 'IN_TRANSIT'));
    timeline.apply(statusEvent(TN, 4, 'IN_TRANSIT'));
    timeline.apply(statusEvent(TN, 3, 'PICKED_UP'));
    timeline.apply(statusEvent(TN, 5, 'OUT_FOR_DELIVERY'));
    timeline.apply(statusEvent(TN, 5, 'OUT_FOR_DELIVERY'));

    expect(timeline.items().map((i) => i.status)).toEqual(['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY']);
  });

  it('ignores events for another parcel', () => {
    expect(timeline.apply(statusEvent('SD-20261104-OTHER1', 9, 'DELIVERED'))).toBe(false);
    expect(timeline.status()).toBe('PICKED_UP');
  });

  it('a re-queued parcel (FAILED then CREATED) shows both rows', () => {
    timeline.apply(statusEvent(TN, 4, 'FAILED'));
    timeline.apply(statusEvent(TN, 5, 'CREATED'));
    expect(timeline.items().map((i) => i.status)).toEqual(['CREATED', 'PICKED_UP', 'FAILED', 'CREATED']);
  });

  it('clear() forgets the parcel, so late events cannot change the view', () => {
    timeline.clear();
    expect(timeline.apply(statusEvent(TN, 9, 'DELIVERED'))).toBe(false);
    expect(timeline.items()).toEqual([]);
    expect(timeline.status()).toBeNull();
  });
});
