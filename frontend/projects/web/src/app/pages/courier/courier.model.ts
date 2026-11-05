export type DeliveryStatus =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  // Legacy aliases for backwards compatibility
  | 'ASSIGNED'
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
  pickupAddress?: string;
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

export interface CourierDeliveriesResponse {
  date: string;
  totalDeliveries: number;
  pendingCount: number;
  completedCount: number;
  deliveries: CourierDeliveryCard[];
}

export interface DeliveryProofInfo {
  proofId: string;
  proofType?: string;
  proofUrl: string;
  recipientName: string;
  uploadedAt: string;
}

export interface CourierDeliveryDetail {
  parcelId: string;
  trackingNumber: string;
  status: string;
  recipientName: string;
  recipientPhone: string;
  senderName?: string;
  senderPhone?: string;
  pickupAddress: string;
  dropoffAddress: string;
  googleMapsUrl?: string | null;
  specialInstructions?: string | null;
  weightKg: number;
  attemptCount: number;
  allowedNextStatuses: string[];
  proofRequired?: boolean;
  proof?: DeliveryProofInfo | null;
  failureReason?: string | null;
}

export interface CourierStatusUpdateRequest {
  status: string;
  reason?: string;
  recipientName?: string;
  idempotencyKey?: string;
}

export interface CourierStatusUpdateResponse {
  parcelId: string;
  trackingNumber: string;
  previousStatus: string;
  currentStatus: string;
  updatedAt: string;
  allowedNextStatuses: string[];
}

export interface ProofUploadPayload {
  file: File;
  recipientName?: string;
  notes?: string;
  deliveryNotes?: string;
  idempotencyKey?: string;
}

export interface ProofUploadResponse {
  proofId: string;
  parcelId: string;
  proofUrl: string;
  mimeType: string;
  fileSizeBytes: number;
  uploadedAt: string;
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
  courierId?: string;
  status: DeliveryStatus;
  recipientName: string;
  recipientPhone: string;
  pickupAddress?: string;
  dropoffAddress?: string;
  recipientAddress?: CourierAddress;
  senderName?: string;
  senderPhone?: string;
  senderAddress?: CourierAddress;
  specialInstructions?: string | null;
  weightKg: number;
  sizeCategory?: 'SMALL' | 'MEDIUM' | 'LARGE';
  timeSlot?: string | null;
  allowedNextStatuses?: string[];
  proofRequired?: boolean;
  proof?: DeliveryProofInfo | DeliveryProof | null;
  failureReason?: string | null;
  googleMapsUrl?: string | null;
  attemptCount?: number;
  sequenceOrder?: number;
  updatedAt?: string;
  createdAt?: string;
}

export interface StatusUpdateRequest {
  status: DeliveryStatus;
  idempotencyKey?: string;
  reason?: string;
  recipientName?: string;
}

export interface CourierStats {
  total: number;
  created: number;
  assigned: number; // alias for created
  pickedUp: number;
  inTransit: number;
  outForDelivery: number;
  delivered: number;
  failed: number;
}
