export interface AddressInfo {
  line1: string;
  line2?: string;
  city: string;
  district: string;
  postalCode: string;
}

export interface DispatchParcelSummary {
  parcelId: string;
  trackingNumber: string;
  status: string;
  pickupZone: string;
  dropoffZone: string;
  pickupAddress?: string;
  dropoffAddress?: string;
  weightKg?: number;
  attemptCount?: number;
  failureReason?: string | null;
  createdAt: string;
  recipientName?: string;
  recipientPhone?: string;
  assignedCourierId?: string;
}

export type UnassignedParcel = DispatchParcelSummary;

export interface UnassignedParcelsPage {
  content: DispatchParcelSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface FailedParcelsPage {
  content: DispatchParcelSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface RequeueRequest {
  reason?: string;
}

export interface RequeueResponse {
  parcelId: string;
  status: string;
  attemptCount: number;
  requeuedAt: string;
}

export interface BulkAssignRequest {
  assignments: AssignParcelRequest[];
}

export interface BulkAssignItemResult {
  parcelId: string;
  courierId: string;
  success: boolean;
  errorCode?: string | null;
  errorMessage?: string | null;
}

export interface BulkAssignResponse {
  totalRequested: number;
  successfulCount: number;
  failedCount: number;
  results: BulkAssignItemResult[];
}

export interface CourierWorkloadItem {
  courierId: string;
  name: string;
  phoneNumber?: string;
  zone?: string;
  activeCount: number;
  available: boolean;
  vehicleType?: string;
}

export type CourierWorkload = CourierWorkloadItem;

export interface AssignParcelRequest {
  parcelId: string;
  courierId: string;
  reassign?: boolean;
}

export type AssignParcelPayload = AssignParcelRequest;

export interface AssignmentResult {
  parcelId: string;
  courierId: string;
  assignedAt: string;
  status: string;
}

export interface CourierAvailabilityUpdateRequest {
  available: boolean;
}

export interface ErrorResponse {
  code: string;
  message: string;
  correlationId: string;
  timestamp: string;
}

export interface Zone {
  id: string;
  name: string;
  districts: string[];
  served: boolean;
  baseFeeLkr: number;
  createdAt: string;
  updatedAt: string;
}

export interface ZoneRequest {
  name: string;
  districts: string[];
  served: boolean;
  baseFeeLkr: number;
}

export type PricingRuleKind = 'WEIGHT_BAND' | 'SIZE';

export interface PricingRule {
  id: string;
  kind: PricingRuleKind;
  key: string;
  minimumWeightKg?: number;
  maximumWeightKg?: number;
  chargeLkr: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PricingRuleRequest {
  kind: PricingRuleKind;
  key: string;
  minimumWeightKg?: number;
  maximumWeightKg?: number;
  chargeLkr: number;
  active: boolean;
}

export interface StaffUser {
  id: string;
  email: string;
  fullName: string;
  phoneNumber: string;
  role: 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'DISPATCHER' | 'ADMIN';
  enabled: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateStaffPayload {
  email: string;
  password: string;
  fullName: string;
  phoneNumber: string;
  role: 'COURIER' | 'DISPATCHER';
}

export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export interface QuoteBreakdown {
  dropoffZoneBaseFee: number;
  weightBandCharge: number;
  sizeCharge: number;
}

export interface QuoteResponse {
  currency: string;
  totalFee: number;
  breakdown: QuoteBreakdown;
  pickupZone?: string;
  dropoffZone?: string;
}

export interface QuoteQuery {
  pickupDistrict: string;
  dropoffDistrict: string;
  weightKg: number;
  size: ParcelSize;
}

export const CANONICAL_DISTRICTS: string[] = [
  'Ampara',
  'Anuradhapura',
  'Badulla',
  'Batticaloa',
  'Colombo',
  'Galle',
  'Gampaha',
  'Hambantota',
  'Jaffna',
  'Kalutara',
  'Kandy',
  'Kegalle',
  'Kilinochchi',
  'Kurunegala',
  'Mannar',
  'Matale',
  'Matara',
  'Monaragala',
  'Mullaitivu',
  'Nuwara Eliya',
  'Polonnaruwa',
  'Puttalam',
  'Ratnapura',
  'Trincomalee',
  'Vavuniya',
];
