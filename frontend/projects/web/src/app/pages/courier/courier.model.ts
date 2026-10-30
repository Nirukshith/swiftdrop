export type DeliveryStatus =
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'FAILED_DELIVERY';

export type FailureReasonCode =
  | 'RECIPIENT_UNAVAILABLE'
  | 'INCORRECT_ADDRESS'
  | 'CUSTOMER_REJECTED'
  | 'ACCESS_RESTRICTED'
  | 'OTHER';

export interface CourierProfile {
  userId: string;
  name: string;
  phone: string | null;
  zone: string | null;
  availability: boolean;
  vehicle: string | null;
}

export interface CourierDeliveryCard {
  parcelId: string;
  trackingNumber: string;
  status: string;
  recipientName: string;
  recipientPhone: string | null;
  dropoffAddress: string;
  googleMapsUrl?: string | null;
  weightKg: number;
  attemptCount: number;
  sequenceOrder: number;
  completedAt?: string | null;
}

export interface CourierDailySummary {
  date: string;
  totalDeliveries: number;
  pendingCount: number;
  completedCount: number;
  deliveries: CourierDeliveryCard[];
}

export interface CourierAddress {
  line1: string;
  line2?: string | null;
  city: string;
  district: string;
  postalCode?: string | null;
}

export interface DeliveryProof {
  id: string;
  parcelId: string;
  courierId: string;
  fileName: string;
  fileSize: number;
  contentType: string;
  recipientName: string;
  deliveryNotes?: string | null;
  uploadedAt: string;
  proofUrl?: string;
}

export interface CourierDelivery {
  parcelId: string;
  trackingNumber: string;
  courierId: string;
  status: DeliveryStatus;
  recipientName: string;
  recipientPhone: string;
  recipientAddress: CourierAddress;
  senderName: string;
  senderAddress: CourierAddress;
  specialInstructions?: string | null;
  weightKg: number;
  sizeCategory: 'SMALL' | 'MEDIUM' | 'LARGE';
  timeSlot?: string | null;
  proof?: DeliveryProof | null;
  failureReason?: string | null;
  updatedAt: string;
  createdAt: string;
}

export interface StatusUpdateRequest {
  status: DeliveryStatus;
  idempotencyKey?: string;
  reason?: string;
}

export interface ProofUploadPayload {
  file: File;
  recipientName: string;
  deliveryNotes?: string;
  idempotencyKey?: string;
}

export interface CourierStats {
  total: number;
  assigned: number;
  pickedUp: number;
  inTransit: number;
  delivered: number;
  failed: number;
}
