import { MOCK_CUSTOMER_ID, buildSeedAddresses, buildSeedData } from './mock-data';
import {
  AddressDto,
  ErrorResponse,
  ParcelHistoryEntry,
  ParcelHistoryResponse,
  ParcelPageResponse,
  ParcelResponse,
  ParcelStatus,
  PublicTrackingResponse,
  RatingResponse,
  SavedAddressResponse,
} from './mock-models';

/** What a handler sends back: HTTP status plus JSON body (or nothing for 204). */
export interface MockResult<T = unknown> {
  status: number;
  body?: T;
  headers?: Record<string, string>;
}

const TRACKING_NO = /^SD-\d{8}-[A-Z0-9]{6}$/;
const PARCEL_STATUSES: ParcelStatus[] = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED'];

function now(): string {
  return new Date().toISOString();
}

function uuid(): string {
  return globalThis.crypto?.randomUUID?.() ?? 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/x/g, () => Math.floor(Math.random() * 16).toString(16));
}

export function error(status: number, code: string, message: string): MockResult<ErrorResponse> {
  return { status, body: { code, message, correlationId: uuid(), timestamp: now() } };
}

/** `Sunil Silva` -> `S*** S***` (docs/tracking-design.md section 2). */
export function maskName(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => `${part.charAt(0).toUpperCase()}***`)
    .join(' ');
}

function int(value: string | null, fallback: number): number {
  const parsed = value === null ? NaN : Number(value);
  return Number.isInteger(parsed) ? parsed : fallback;
}

function validAddress(address: Partial<AddressDto> | undefined): boolean {
  return !!address && !!address.line1 && !!address.city && !!address.district && !!address.postalCode;
}

/**
 * In-memory backend for the mock layer. Behaviour follows the contracts: paging and filters,
 * cancel only while CREATED (409 otherwise), masked public tracking, rating only when DELIVERED
 * and once per parcel. State lives until the page reloads.
 */
export class MockParcelDb {
  private parcels: ParcelResponse[] = [];
  private history = new Map<string, ParcelHistoryEntry[]>();
  private addresses: SavedAddressResponse[] = [];
  private ratings = new Map<string, RatingResponse>();

  constructor() {
    this.reset();
  }

  reset(): void {
    const seed = buildSeedData();
    this.parcels = seed.parcels;
    this.history = seed.history;
    this.addresses = buildSeedAddresses();
    this.ratings = new Map();
  }

  // ---------------------------------------------------------------- parcels (parcel.yaml)

  listParcels(params: URLSearchParams): MockResult<ParcelPageResponse | ErrorResponse> {
    const status = params.get('status');
    if (status && !PARCEL_STATUSES.includes(status as ParcelStatus)) {
      return error(400, 'VALIDATION_FAILED', `Unknown status ${status}`);
    }
    const page = Math.max(0, int(params.get('page'), 0));
    const size = Math.min(50, Math.max(1, int(params.get('size'), 20)));
    const matching = this.parcels
      .filter((parcel) => !status || parcel.status === status)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return {
      status: 200,
      body: {
        content: matching.slice(page * size, page * size + size),
        totalElements: matching.length,
        totalPages: Math.ceil(matching.length / size),
        page,
        size,
      },
    };
  }

  getParcel(id: string): MockResult<ParcelResponse | ErrorResponse> {
    const parcel = this.find(id);
    return parcel ? { status: 200, body: parcel } : this.parcelNotFound(id);
  }

  createParcel(request: Record<string, unknown>): MockResult<ParcelResponse | ErrorResponse> {
    const weight = Number(request['weightKg']);
    const missing = ['recipientName', 'recipientPhone', 'size'].filter((field) => !request[field]);
    if (!validAddress(request['pickupAddress'] as AddressDto) || !validAddress(request['dropoffAddress'] as AddressDto)) {
      missing.push('pickupAddress/dropoffAddress');
    }
    if (missing.length) {
      return error(400, 'VALIDATION_FAILED', `Missing or invalid: ${missing.join(', ')}`);
    }
    if (!(weight >= 0.1 && weight <= 50)) {
      return error(400, 'VALIDATION_FAILED', 'weightKg must be between 0.1 and 50');
    }
    const created = now();
    const date = created.slice(0, 10).replace(/-/g, '');
    const suffix = Array.from({ length: 6 }, () => 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'[Math.floor(Math.random() * 32)]).join('');
    const parcel: ParcelResponse = {
      id: uuid(),
      trackingNumber: `SD-${date}-${suffix}`,
      status: 'CREATED',
      customerId: MOCK_CUSTOMER_ID,
      merchantId: null,
      courierId: null,
      pickupAddress: request['pickupAddress'] as AddressDto,
      dropoffAddress: request['dropoffAddress'] as AddressDto,
      weightKg: weight,
      size: request['size'] as ParcelResponse['size'],
      // Mock fee only; the real fee comes from operations-service's quote (M5).
      fee: Math.round(350 + weight * 100),
      attemptCount: 0,
      failureReason: null,
      recipientName: String(request['recipientName']),
      recipientPhone: String(request['recipientPhone']),
      notes: (request['notes'] as string | undefined) ?? null,
      proof: null,
      sequence: 1,
      createdAt: created,
      updatedAt: created,
    };
    this.parcels.push(parcel);
    this.history.set(parcel.id, [{ sequence: 1, fromStatus: null, toStatus: 'CREATED', occurredAt: created }]);
    return { status: 201, body: parcel };
  }

  cancelParcel(id: string, reason?: string | null): MockResult<ParcelResponse | ErrorResponse> {
    const parcel = this.find(id);
    if (!parcel) {
      return this.parcelNotFound(id);
    }
    if (parcel.status !== 'CREATED') {
      return error(409, 'ILLEGAL_STATUS_TRANSITION', `Cannot cancel parcel in status ${parcel.status}. Cancellation only permitted before pickup.`);
    }
    const changed = now();
    parcel.status = 'CANCELLED';
    parcel.updatedAt = changed;
    parcel.sequence = (parcel.sequence ?? 1) + 1;
    this.history.get(id)?.push({ sequence: parcel.sequence, fromStatus: 'CREATED', toStatus: 'CANCELLED', occurredAt: changed, reason: reason ?? null });
    return { status: 200, body: parcel };
  }

  // ---------------------------------------------------------------- addresses (parcel.yaml)

  listAddresses(): MockResult<SavedAddressResponse[]> {
    return { status: 200, body: this.addresses };
  }

  getAddress(id: string): MockResult<SavedAddressResponse | ErrorResponse> {
    const address = this.addresses.find((a) => a.id === id);
    return address ? { status: 200, body: address } : error(404, 'RESOURCE_NOT_FOUND', `Address not found with ID ${id}`);
  }

  saveAddress(request: Record<string, unknown>, id?: string): MockResult<SavedAddressResponse | ErrorResponse> {
    if (!request['label'] || !validAddress(request as Partial<AddressDto>)) {
      return error(400, 'VALIDATION_FAILED', 'label, line1, city, district and postalCode are required');
    }
    const existing = id ? this.addresses.find((a) => a.id === id) : undefined;
    if (id && !existing) {
      return error(404, 'RESOURCE_NOT_FOUND', `Address not found with ID ${id}`);
    }
    const address: SavedAddressResponse = {
      id: existing?.id ?? uuid(),
      customerId: MOCK_CUSTOMER_ID,
      label: String(request['label']),
      line1: String(request['line1']),
      line2: (request['line2'] as string | undefined) ?? null,
      city: String(request['city']),
      district: String(request['district']),
      postalCode: String(request['postalCode']),
      isDefault: Boolean(request['isDefault']),
      createdAt: existing?.createdAt ?? now(),
      updatedAt: existing ? now() : null,
    };
    if (address.isDefault) {
      this.addresses.forEach((a) => (a.isDefault = false));
    }
    this.addresses = existing ? this.addresses.map((a) => (a.id === id ? address : a)) : [...this.addresses, address];
    return { status: existing ? 200 : 201, body: address };
  }

  deleteAddress(id: string): MockResult<ErrorResponse | undefined> {
    const before = this.addresses.length;
    this.addresses = this.addresses.filter((a) => a.id !== id);
    return this.addresses.length < before ? { status: 204 } : error(404, 'RESOURCE_NOT_FOUND', `Address not found with ID ${id}`);
  }

  // ---------------------------------------------------------------- tracking (parcel-read.yaml)

  publicTracking(trackingNo: string): MockResult<PublicTrackingResponse | ErrorResponse> {
    const parcel = TRACKING_NO.test(trackingNo) ? this.parcels.find((p) => p.trackingNumber === trackingNo) : undefined;
    if (!parcel) {
      // Same 404 for unknown and malformed numbers, as the contract requires.
      return error(404, 'RESOURCE_NOT_FOUND', 'No parcel found for this tracking number');
    }
    return {
      status: 200,
      body: {
        trackingNumber: parcel.trackingNumber,
        status: parcel.status,
        recipientNameMasked: maskName(parcel.recipientName),
        dropoffDistrict: parcel.dropoffAddress.district,
        lastUpdatedAt: parcel.updatedAt,
        timeline: (this.history.get(parcel.id) ?? []).map((entry) => ({ status: entry.toStatus, occurredAt: entry.occurredAt })),
      },
    };
  }

  findByTrackingNumber(trackingNo: string): ParcelResponse | undefined {
    return this.parcels.find((p) => p.trackingNumber === trackingNo);
  }

  parcelHistory(id: string): MockResult<ParcelHistoryResponse | ErrorResponse> {
    const parcel = this.find(id);
    if (!parcel) {
      return this.parcelNotFound(id);
    }
    return {
      status: 200,
      body: { parcelId: parcel.id, trackingNumber: parcel.trackingNumber, currentStatus: parcel.status, entries: this.history.get(id) ?? [] },
    };
  }

  rateParcel(id: string, request: Record<string, unknown>): MockResult<RatingResponse | ErrorResponse> {
    const parcel = this.find(id);
    if (!parcel) {
      return this.parcelNotFound(id);
    }
    const score = Number(request['score']);
    const comment = request['comment'] as string | null | undefined;
    if (!Number.isInteger(score) || score < 1 || score > 5) {
      return error(400, 'VALIDATION_FAILED', 'score must be between 1 and 5');
    }
    if (comment && comment.length > 500) {
      return error(400, 'VALIDATION_FAILED', 'comment must be at most 500 characters');
    }
    if (parcel.status !== 'DELIVERED') {
      return error(409, 'PARCEL_NOT_DELIVERED', `Only delivered parcels can be rated; current status is ${parcel.status}`);
    }
    if (this.ratings.has(id)) {
      return error(409, 'RATING_ALREADY_EXISTS', `Parcel ${parcel.trackingNumber} has already been rated`);
    }
    const rating: RatingResponse = { parcelId: id, score, comment: comment ?? null, createdAt: now() };
    this.ratings.set(id, rating);
    return { status: 201, body: rating };
  }

  getRating(id: string): MockResult<RatingResponse | ErrorResponse> {
    if (!this.find(id)) {
      return this.parcelNotFound(id);
    }
    const rating = this.ratings.get(id);
    return rating ? { status: 200, body: rating } : error(404, 'RESOURCE_NOT_FOUND', `No rating found for parcel ${id}`);
  }

  private find(id: string): ParcelResponse | undefined {
    return this.parcels.find((parcel) => parcel.id === id);
  }

  private parcelNotFound(id: string): MockResult<ErrorResponse> {
    return error(404, 'RESOURCE_NOT_FOUND', `Parcel not found with ID ${id}`);
  }
}

/** Shared instance used by the browser worker. Tests create their own. */
export const mockDb = new MockParcelDb();
