export interface AddressInfo {
  line1: string;
  line2?: string;
  city: string;
  district: string;
  postalCode: string;
}

export interface UnassignedParcel {
  parcelId: string;
  trackingNumber: string;
  status: 'CREATED' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED' | 'CANCELLED';
  pickupZone: string;
  dropoffZone: string;
  pickupAddress: string;
  dropoffAddress: string;
  weightKg: number;
  attemptCount: number;
  failureReason: string | null;
  createdAt: string;
  recipientName: string;
  recipientPhone: string;
  assignedCourierId?: string;
}

export interface CourierWorkload {
  courierId: string;
  name: string;
  phoneNumber: string;
  zone: string;
  activeCount: number;
  available: boolean;
  vehicleType: string;
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

export interface AssignParcelPayload {
  parcelId: string;
  courierId: string;
}
