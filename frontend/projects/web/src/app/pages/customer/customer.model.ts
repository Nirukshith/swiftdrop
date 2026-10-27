export type District =
  | 'Ampara'
  | 'Anuradhapura'
  | 'Badulla'
  | 'Batticaloa'
  | 'Colombo'
  | 'Galle'
  | 'Gampaha'
  | 'Hambantota'
  | 'Jaffna'
  | 'Kalutara'
  | 'Kandy'
  | 'Kegalle'
  | 'Kilinochchi'
  | 'Kurunegala'
  | 'Mannar'
  | 'Matale'
  | 'Matara'
  | 'Monaragala'
  | 'Mullaitivu'
  | 'Nuwara Eliya'
  | 'Polonnaruwa'
  | 'Puttalam'
  | 'Ratnapura'
  | 'Trincomalee'
  | 'Vavuniya';

export const SRI_LANKA_DISTRICTS: District[] = [
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

export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export type ParcelStatus =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'CANCELLED';

export interface AddressDto {
  line1: string;
  line2?: string | null;
  city: string;
  district: District;
  postalCode: string;
}

export interface CustomerAddress extends AddressDto {
  id: string;
  customerId: string;
  label: string;
  isDefault: boolean;
  createdAt?: string;
}

export interface CreateCustomerParcelRequest {
  pickupAddress: AddressDto;
  dropoffAddress: AddressDto;
  weightKg: number;
  size: ParcelSize;
  recipientName: string;
  recipientPhone: string;
  notes?: string | null;
}

export interface CustomerParcel {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  pickupAddress: AddressDto;
  dropoffAddress: AddressDto;
  weightKg: number;
  size: ParcelSize;
  fee: number;
  attemptCount: number;
  recipientName: string;
  recipientPhone: string;
  notes?: string | null;
  failureReason?: string | null;
  createdAt: string;
  updatedAt?: string;
}

export interface FeePreview {
  baseFee: number;
  interDistrictFee: number;
  weightFee: number;
  sizeTierFee: number;
  totalFee: number;
  pickupDistrict: District;
  dropoffDistrict: District;
  weightKg: number;
  size: ParcelSize;
}
