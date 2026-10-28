import { MockParcelDb, maskName } from './mock-db';
import { ParcelPageResponse, ParcelResponse, PublicTrackingResponse, ErrorResponse } from './mock-models';

describe('MockParcelDb', () => {
  let db: MockParcelDb;

  beforeEach(() => (db = new MockParcelDb()));

  const createRequest = () => ({
    pickupAddress: { line1: '45 Galle Road', city: 'Colombo 03', district: 'Colombo', postalCode: '00300' },
    dropoffAddress: { line1: '12 Temple Road', city: 'Kandy', district: 'Kandy', postalCode: '20000' },
    weightKg: 2,
    size: 'SMALL',
    recipientName: 'Test Person',
    recipientPhone: '+94770000000',
  });

  describe('parcels', () => {
    it('pages newest first with the contract page shape', () => {
      const result = db.listParcels(new URLSearchParams('page=0&size=4'));
      const page = result.body as ParcelPageResponse;
      expect(result.status).toBe(200);
      expect(page.content.length).toBe(4);
      expect(page.totalElements).toBe(6);
      expect(page.totalPages).toBe(2);
      const dates = page.content.map((p) => p.createdAt);
      expect([...dates].sort().reverse()).toEqual(dates);
    });

    it('filters by status and rejects unknown statuses', () => {
      const delivered = db.listParcels(new URLSearchParams('status=DELIVERED')).body as ParcelPageResponse;
      expect(delivered.content.every((p) => p.status === 'DELIVERED')).toBe(true);
      expect(db.listParcels(new URLSearchParams('status=LOST')).status).toBe(400);
    });

    it('caps the page size at 50 like the contract', () => {
      expect((db.listParcels(new URLSearchParams('size=500')).body as ParcelPageResponse).size).toBe(50);
    });

    it('creates a CREATED parcel with a valid tracking number and validates input', () => {
      const created = db.createParcel(createRequest());
      expect(created.status).toBe(201);
      const parcel = created.body as ParcelResponse;
      expect(parcel.status).toBe('CREATED');
      expect(parcel.trackingNumber).toMatch(/^SD-\d{8}-[A-Z0-9]{6}$/);
      expect(db.getParcel(parcel.id).status).toBe(200);

      expect(db.createParcel({ ...createRequest(), weightKg: 99 }).status).toBe(400);
      expect(db.createParcel({ ...createRequest(), recipientName: '' }).status).toBe(400);
    });

    it('cancels only while CREATED, otherwise 409 ILLEGAL_STATUS_TRANSITION', () => {
      const created = db.createParcel(createRequest()).body as ParcelResponse;
      const cancelled = db.cancelParcel(created.id, 'Changed my mind');
      expect((cancelled.body as ParcelResponse).status).toBe('CANCELLED');

      const again = db.cancelParcel(created.id);
      expect(again.status).toBe(409);
      expect((again.body as ErrorResponse).code).toBe('ILLEGAL_STATUS_TRANSITION');
    });

    it('returns the shared error shape for unknown parcels', () => {
      const result = db.getParcel('nope');
      const body = result.body as ErrorResponse;
      expect(result.status).toBe(404);
      expect(body.code).toBe('RESOURCE_NOT_FOUND');
      expect(body.correlationId).toBeTruthy();
      expect(body.timestamp).toBeTruthy();
    });
  });

  describe('addresses', () => {
    it('creates, updates, keeps one default and deletes', () => {
      const created = db.saveAddress({ label: 'Mum', line1: '1 Hill St', city: 'Kandy', district: 'Kandy', postalCode: '20000', isDefault: true });
      expect(created.status).toBe(201);
      const list = db.listAddresses().body!;
      expect(list.filter((a) => a.isDefault).length).toBe(1);

      const id = (created.body as { id: string }).id;
      expect(db.saveAddress({ label: 'Mum (new)', line1: '2 Hill St', city: 'Kandy', district: 'Kandy', postalCode: '20000' }, id).status).toBe(200);
      expect(db.deleteAddress(id).status).toBe(204);
      expect(db.deleteAddress(id).status).toBe(404);
      expect(db.saveAddress({ label: 'Bad' }).status).toBe(400);
    });
  });

  describe('public tracking', () => {
    it('returns only masked, allow-listed fields', () => {
      const result = db.publicTracking('SD-20261026-A1B2C3');
      const body = result.body as PublicTrackingResponse;
      expect(result.status).toBe(200);
      expect(Object.keys(body).sort()).toEqual(['dropoffDistrict', 'lastUpdatedAt', 'recipientNameMasked', 'status', 'timeline', 'trackingNumber']);
      expect(body.recipientNameMasked).toBe('S*** S***');
      expect(body.dropoffDistrict).toBe('Kandy');
      expect(JSON.stringify(body)).not.toContain('+9477');
      expect(JSON.stringify(body)).not.toContain('Temple Road');
      expect(body.timeline.map((t) => t.status)).toEqual(['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY']);
    });

    it('gives the same 404 for unknown and malformed numbers', () => {
      const unknown = db.publicTracking('SD-20990101-ZZZZZZ');
      const malformed = db.publicTracking('not-a-number');
      expect(unknown.status).toBe(404);
      expect(malformed.status).toBe(404);
      expect((unknown.body as ErrorResponse).message).toBe((malformed.body as ErrorResponse).message);
    });

    it('masks every name part with a fixed length', () => {
      expect(maskName('Sunil Silva')).toBe('S*** S***');
      expect(maskName('  kavindu  ')).toBe('K***');
    });
  });

  describe('history and rating', () => {
    it('returns the timeline oldest first with a null fromStatus for creation', () => {
      const result = db.parcelHistory('1b6f0c3e-2a44-4d9b-9f7e-0c1d2e3f4a51');
      const entries = (result.body as { entries: { fromStatus: string | null; toStatus: string }[] }).entries;
      expect(entries[0]).toEqual(expect.objectContaining({ fromStatus: null, toStatus: 'CREATED' }));
      expect(entries[entries.length - 1].toStatus).toBe('DELIVERED');
    });

    it('rates delivered parcels once and rejects the rest', () => {
      const delivered = '1b6f0c3e-2a44-4d9b-9f7e-0c1d2e3f4a51';
      expect(db.getRating(delivered).status).toBe(404);
      expect(db.rateParcel(delivered, { score: 5, comment: 'Great' }).status).toBe(201);
      expect(db.getRating(delivered).status).toBe(200);
      expect((db.rateParcel(delivered, { score: 4 }).body as ErrorResponse).code).toBe('RATING_ALREADY_EXISTS');

      const outForDelivery = '7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11';
      expect((db.rateParcel(outForDelivery, { score: 5 }).body as ErrorResponse).code).toBe('PARCEL_NOT_DELIVERED');
      expect(db.rateParcel(delivered, { score: 9 }).status).toBe(400);
    });
  });

  it('reset() restores the seed data', () => {
    db.cancelParcel('2c7a1d4f-3b55-4eac-8a8f-1d2e3f4a5b62');
    db.reset();
    expect((db.getParcel('2c7a1d4f-3b55-4eac-8a8f-1d2e3f4a5b62').body as ParcelResponse).status).toBe('CREATED');
  });
});
