/**
 * Tracking shapes copied from contracts/openapi/parcel-read.yaml (M2) and the parcel detail
 * fields of contracts/openapi/parcel.yaml (M1). Keep them in sync.
 */

export type ParcelStatus =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'CANCELLED';

export const PARCEL_STATUSES: readonly ParcelStatus[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'FAILED',
  'CANCELLED',
];

/** The usual forward path, used to show the steps still to come. */
export const HAPPY_PATH: readonly ParcelStatus[] = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'];

/** The server ends the stream after these statuses. */
export const TERMINAL_STATUSES: readonly ParcelStatus[] = ['DELIVERED', 'CANCELLED'];

export interface PublicTimelineEntry {
  status: ParcelStatus;
  occurredAt: string;
}

export interface PublicTrackingResponse {
  trackingNumber: string;
  status: ParcelStatus;
  recipientNameMasked: string;
  dropoffDistrict: string;
  lastUpdatedAt: string;
  timeline: PublicTimelineEntry[];
}

/** Body of an SSE `status` event. */
export interface PublicTrackingEvent {
  trackingNumber: string;
  sequence: number;
  status: ParcelStatus;
  occurredAt: string;
}

export interface ParcelHistoryEntry {
  sequence: number | null;
  fromStatus: ParcelStatus | null;
  toStatus: ParcelStatus;
  occurredAt: string;
  reason?: string | null;
}

export interface ParcelHistoryResponse {
  parcelId: string;
  trackingNumber: string;
  currentStatus: ParcelStatus;
  entries: ParcelHistoryEntry[];
}

export interface ParcelAddress {
  line1: string;
  line2?: string | null;
  city: string;
  district: string;
  postalCode: string;
}

/** Fields of M1's ParcelResponse that the detail page shows. */
export interface ParcelDetail {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  pickupAddress: ParcelAddress;
  dropoffAddress: ParcelAddress;
  recipientName: string;
  weightKg: number;
  size: string;
  fee: number;
  createdAt: string;
  updatedAt: string;
}

/** One row of the timeline component. */
export interface TimelineItem {
  status: ParcelStatus;
  occurredAt: string;
  reason?: string | null;
}
