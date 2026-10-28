/**
 * Response shapes copied from the merged contracts. Keep them in sync:
 * - contracts/openapi/parcel.yaml       (ParcelResponse, ParcelPageResponse, SavedAddressResponse, ErrorResponse)
 * - contracts/openapi/parcel-read.yaml  (PublicTrackingResponse, ParcelHistoryResponse, RatingResponse)
 */

export type ParcelStatus = 'CREATED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED' | 'CANCELLED';
export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export interface AddressDto {
  line1: string;
  line2?: string | null;
  city: string;
  district: string;
  postalCode: string;
}

export interface DeliveryProof {
  recipientName?: string;
  proofPhotoPath?: string | null;
}

export interface ParcelResponse {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  customerId?: string | null;
  merchantId?: string | null;
  courierId?: string | null;
  pickupAddress: AddressDto;
  dropoffAddress: AddressDto;
  weightKg: number;
  size: ParcelSize;
  fee: number;
  attemptCount: number;
  failureReason?: string | null;
  recipientName: string;
  recipientPhone: string;
  notes?: string | null;
  proof?: DeliveryProof | null;
  sequence?: number;
  createdAt: string;
  updatedAt: string;
}

export interface ParcelPageResponse {
  content: ParcelResponse[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface SavedAddressResponse extends AddressDto {
  id: string;
  customerId: string;
  label: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ErrorResponse {
  code: string;
  message: string;
  correlationId: string;
  timestamp: string;
}

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

export interface RatingResponse {
  parcelId: string;
  score: number;
  comment?: string | null;
  createdAt: string;
}
