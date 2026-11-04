import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TrackComponent } from './track.component';
import { FakeEventSource, fakeEventSourceProvider, statusEvent } from './tracking/testing';
import { PublicTrackingResponse } from './tracking/tracking.model';

const TN = 'SD-20261104-A1B2C3';
const OTHER = 'SD-20261104-Z9Y8X7';

function lookup(trackingNumber: string, status: PublicTrackingResponse['status'] = 'PICKED_UP'): PublicTrackingResponse {
  return {
    trackingNumber,
    status,
    recipientNameMasked: 'S*** S***',
    dropoffDistrict: 'Kandy',
    lastUpdatedAt: '2026-11-04T09:00:00Z',
    timeline: [
      { status: 'CREATED', occurredAt: '2026-11-04T08:00:00Z' },
      { status, occurredAt: '2026-11-04T09:00:00Z' },
    ],
  };
}

describe('TrackComponent (public tracking page)', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  beforeEach(async () => {
    FakeEventSource.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'track/:trackingNo', component: TrackComponent }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        fakeEventSourceProvider,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = (testId: string) => el().querySelector(`[data-testid="${testId}"]`)?.textContent?.trim();
  const rows = () => Array.from(el().querySelectorAll('app-tracking-timeline li'));
  const doneRows = () => rows().filter((li) => li.getAttribute('data-state') !== 'pending');
  const indicator = () => el().querySelector('app-live-indicator [data-state]')?.getAttribute('data-state');

  async function open(trackingNumber: string, response: PublicTrackingResponse = lookup(trackingNumber)) {
    await harness.navigateByUrl(`/track/${trackingNumber}`);
    expect(el().textContent).toContain('Looking up your parcel');
    http.expectOne(`/api/v1/tracking/${trackingNumber}`).flush(response);
    harness.detectChanges();
  }

  it('shows the masked lookup from the API and the timeline with the steps still to come', async () => {
    await open(TN);

    expect(text('tracking-number')).toBe(TN);
    expect(text('current-status')).toBe('Picked up');
    expect(el().textContent).toContain('S*** S***');
    expect(el().textContent).toContain('Kandy');
    expect(doneRows().map((li) => li.textContent)).toEqual([expect.stringContaining('Created'), expect.stringContaining('Picked up')]);
    expect(rows().filter((li) => li.getAttribute('data-state') === 'pending')).toHaveLength(3);
  });

  it('connects the stream for this parcel and only says Live once it is open', async () => {
    await open(TN);
    expect(FakeEventSource.latest().url).toBe(`/api/v1/tracking/${TN}/stream`);
    expect(indicator()).toBe('connecting');

    FakeEventSource.latest().open();
    harness.detectChanges();
    expect(indicator()).toBe('live');
  });

  it('adds live updates without a refresh, once each, and ignores other parcels', async () => {
    await open(TN);
    const source = FakeEventSource.latest();
    source.open();
    source.emit(statusEvent(TN, 3, 'PICKED_UP')); // current state at connect
    source.emit(statusEvent(TN, 4, 'IN_TRANSIT', '2026-11-04T10:00:00Z'));
    source.emit(statusEvent(TN, 4, 'IN_TRANSIT', '2026-11-04T10:00:00Z')); // duplicate
    source.emit(statusEvent(OTHER, 9, 'DELIVERED'));
    harness.detectChanges();

    expect(text('current-status')).toBe('In transit');
    expect(doneRows()).toHaveLength(3);
    expect(doneRows()[2].textContent).toContain('In transit');
    expect(doneRows()[2].getAttribute('data-state')).toBe('current');
  });

  it('shows reconnecting and, when the browser gives up, offers a new connection', async () => {
    await open(TN);
    const first = FakeEventSource.latest();
    first.open();
    first.fail(0);
    harness.detectChanges();
    expect(indicator()).toBe('reconnecting');

    first.fail(2);
    harness.detectChanges();
    expect(indicator()).toBe('disconnected');

    (el().querySelector('app-live-indicator button') as HTMLButtonElement).click();
    harness.detectChanges();
    expect(FakeEventSource.instances).toHaveLength(2);
    expect(FakeEventSource.latest().url).toBe(`/api/v1/tracking/${TN}/stream`);
  });

  it('does not open a stream for a delivered parcel', async () => {
    await open(TN, lookup(TN, 'DELIVERED'));
    expect(FakeEventSource.instances).toHaveLength(0);
    expect(indicator()).toBe('ended');
  });

  it('switching to another parcel closes the old stream; its late events do not show', async () => {
    await open(TN);
    const first = FakeEventSource.latest();

    await open(OTHER);
    expect(first.closed).toBe(true);
    first.emit(statusEvent(TN, 9, 'DELIVERED'));
    harness.detectChanges();

    expect(text('tracking-number')).toBe(OTHER);
    expect(text('current-status')).toBe('Picked up');
    expect(FakeEventSource.latest().url).toBe(`/api/v1/tracking/${OTHER}/stream`);
  });

  it('closes the stream when the page is left', async () => {
    await open(TN);
    const source = FakeEventSource.latest();

    harness.fixture.destroy();

    expect(source.closed).toBe(true);
  });

  it('shows not found for an unknown number and an error with retry otherwise', async () => {
    await harness.navigateByUrl(`/track/${TN}`);
    http.expectOne(`/api/v1/tracking/${TN}`).flush(
      { code: 'RESOURCE_NOT_FOUND', message: 'No parcel found for this tracking number' },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();
    expect(el().textContent).toContain('Parcel not found');
    expect(FakeEventSource.instances).toHaveLength(0);

    await harness.navigateByUrl(`/track/${OTHER}`);
    http.expectOne(`/api/v1/tracking/${OTHER}`).flush(
      { code: 'TOO_MANY_REQUESTS', message: 'Too many tracking requests, please retry later' },
      { status: 429, statusText: 'Too Many Requests' },
    );
    harness.detectChanges();
    expect(el().textContent).toContain('Too many tracking requests');

    (el().querySelector('[role="alert"] button') as HTMLButtonElement).click();
    http.expectOne(`/api/v1/tracking/${OTHER}`).flush(lookup(OTHER));
    harness.detectChanges();
    expect(text('tracking-number')).toBe(OTHER);
  });

  it('shows an empty timeline message when there is no history yet', async () => {
    await open(TN, { ...lookup(TN, 'CREATED'), timeline: [] });
    expect(el().textContent).toContain('No updates yet');
  });
});
