/**
 * Shapes used by the customer dashboard and history pages, copied from
 * contracts/openapi/parcel.yaml (ParcelResponse, ParcelPageResponse). Keep them in sync.
 */

export type ParcelStatus =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'CANCELLED';

export const PARCEL_STATUSES: ParcelStatus[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'FAILED',
  'CANCELLED',
];

export interface ParcelAddress {
  line1: string;
  line2?: string | null;
  city: string;
  district: string;
  postalCode: string;
}

/** Fields of ParcelResponse that these pages read. */
export interface CustomerParcel {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  pickupAddress: ParcelAddress;
  dropoffAddress: ParcelAddress;
  recipientName: string;
  fee: number;
  createdAt: string;
  updatedAt: string;
}

export interface ParcelPage {
  content: CustomerParcel[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ParcelListQuery {
  page?: number;
  size?: number;
  status?: ParcelStatus | null;
}
