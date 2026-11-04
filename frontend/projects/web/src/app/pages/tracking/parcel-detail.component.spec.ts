import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from 'shared-auth';
import { ParcelDetailComponent } from './parcel-detail.component';
import { FakeEventSource, fakeEventSourceProvider, statusEvent } from './testing';
import { ParcelDetail, ParcelHistoryResponse } from './tracking.model';

const TN = 'SD-20261104-A1B2C3';
const OTHER_TN = 'SD-20261104-Z9Y8X7';

function parcel(id: string, trackingNumber: string, status: ParcelDetail['status'] = 'IN_TRANSIT'): ParcelDetail {
  return {
    id,
    trackingNumber,
    status,
    pickupAddress: { line1: '45 Galle Road', city: 'Colombo 03', district: 'Colombo', postalCode: '00300' },
    dropoffAddress: { line1: '12 Temple Road', city: 'Kandy', district: 'Kandy', postalCode: '20000' },
    recipientName: 'Sunil Silva',
    weightKg: 2.5,
    size: 'MEDIUM',
    fee: 750,
    createdAt: '2026-11-04T08:00:00Z',
    updatedAt: '2026-11-04T10:00:00Z',
  };
}

function history(id: string, trackingNumber: string): ParcelHistoryResponse {
  return {
    parcelId: id,
    trackingNumber,
    currentStatus: 'IN_TRANSIT',
    entries: [
      { sequence: null, fromStatus: null, toStatus: 'CREATED', occurredAt: '2026-11-04T08:00:00Z', reason: 'Parcel order created' },
      { sequence: null, fromStatus: 'CREATED', toStatus: 'PICKED_UP', occurredAt: '2026-11-04T09:00:00Z' },
      { sequence: null, fromStatus: 'PICKED_UP', toStatus: 'IN_TRANSIT', occurredAt: '2026-11-04T10:00:00Z' },
    ],
  };
}

describe('ParcelDetailComponent', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  const token = signal<string | null>('token');

  beforeEach(async () => {
    FakeEventSource.reset();
    token.set('token');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'customer/parcels/:id', component: ParcelDetailComponent }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        fakeEventSourceProvider,
        { provide: AuthService, useValue: { isAuthenticated: computed(() => !!token()) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  // forkJoin cancels the history request when the parcel request fails.
  afterEach(() => http.verify({ ignoreCancelled: true }));

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = (testId: string) => el().querySelector(`[data-testid="${testId}"]`)?.textContent?.trim();
  const doneRows = () =>
    Array.from(el().querySelectorAll('app-tracking-timeline li')).filter((li) => li.getAttribute('data-state') !== 'pending');

  /** The parcel request fails; the history request is cancelled by forkJoin. */
  async function openFailing(id: string, status: number, body: object = {}) {
    await harness.navigateByUrl(`/customer/parcels/${id}`);
    const history = http.expectOne(`/api/v1/parcels/${id}/history`);
    http.expectOne(`/api/v1/parcels/${id}`).flush(body, { status, statusText: 'Error' });
    expect(history.cancelled).toBe(true);
    harness.detectChanges();
  }

  async function open(id: string, trackingNumber: string) {
    await harness.navigateByUrl(`/customer/parcels/${id}`);
    http.expectOne(`/api/v1/parcels/${id}`).flush(parcel(id, trackingNumber));
    http.expectOne(`/api/v1/parcels/${id}/history`).flush(history(id, trackingNumber));
    harness.detectChanges();
  }

  it('loads the parcel and its history from the API and shows full details to the owner', async () => {
    await open('p-1', TN);

    expect(text('tracking-number')).toBe(TN);
    expect(text('current-status')).toBe('In transit');
    expect(el().textContent).toContain('12 Temple Road');
    expect(el().textContent).toContain('Sunil Silva');
    expect(doneRows().map((li) => li.textContent)).toEqual([
      expect.stringContaining('Created'),
      expect.stringContaining('Picked up'),
      expect.stringContaining('In transit'),
    ]);
    expect(doneRows()[0].textContent).toContain('Parcel order created');
  });

  it('follows the public stream of the parcel and updates the timeline live', async () => {
    await open('p-1', TN);
    const source = FakeEventSource.latest();
    expect(source.url).toBe(`/api/v1/tracking/${TN}/stream`);

    source.open();
    source.emit(statusEvent(TN, 4, 'IN_TRANSIT'));
    source.emit(statusEvent(TN, 5, 'OUT_FOR_DELIVERY'));
    source.emit(statusEvent(TN, 5, 'OUT_FOR_DELIVERY'));
    harness.detectChanges();

    expect(text('current-status')).toBe('Out for delivery');
    expect(doneRows()).toHaveLength(4);
  });

  it('opening another parcel replaces the connection', async () => {
    await open('p-1', TN);
    const first = FakeEventSource.latest();

    await open('p-2', OTHER_TN);

    expect(first.closed).toBe(true);
    expect(FakeEventSource.latest().url).toBe(`/api/v1/tracking/${OTHER_TN}/stream`);
    first.emit(statusEvent(TN, 9, 'DELIVERED'));
    harness.detectChanges();
    expect(text('tracking-number')).toBe(OTHER_TN);
    expect(text('current-status')).toBe('In transit');
  });

  it('logout closes the stream and clears the parcel from the page', async () => {
    await open('p-1', TN);
    const source = FakeEventSource.latest();

    token.set(null);
    harness.detectChanges();

    expect(source.closed).toBe(true);
    expect(el().textContent).toContain('Signed out');
    expect(el().textContent).not.toContain('Temple Road');
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it('another customer gets a no-access message and no stream', async () => {
    await openFailing('p-9', 403, { code: 'FORBIDDEN', message: 'Access denied' });

    expect(el().textContent).toContain('You do not have access to this parcel');
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it('shows not found, signed-out and retryable error states', async () => {
    await openFailing('missing', 404, { message: 'Parcel not found with id: missing' });
    expect(el().textContent).toContain('Parcel not found');

    await openFailing('p-401', 401);
    expect(el().textContent).toContain('Please sign in again');

    await openFailing('p-500', 500, { message: 'Database unavailable' });
    expect(el().textContent).toContain('Database unavailable');

    (el().querySelector('[role="alert"] button') as HTMLButtonElement).click();
    http.expectOne('/api/v1/parcels/p-500').flush(parcel('p-500', TN));
    http.expectOne('/api/v1/parcels/p-500/history').flush(history('p-500', TN));
    harness.detectChanges();
    expect(text('tracking-number')).toBe(TN);
  });
});
