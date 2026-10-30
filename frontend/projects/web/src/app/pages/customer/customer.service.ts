import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import {
  CreateCustomerParcelRequest,
  CustomerAddress,
  CustomerParcel,
  District,
  FeePreview,
  ParcelSize,
  ParcelStatus,
} from './customer.model';

@Injectable({
  providedIn: 'root',
})
export class CustomerService {
  private readonly defaultCustomerId = 'c8b92410-189a-4128-98e2-481928410291';

  private addresses = signal<CustomerAddress[]>([
    {
      id: 'addr-001',
      customerId: this.defaultCustomerId,
      label: 'Home',
      line1: '142 Havelock Road',
      line2: 'Apt 4B',
      city: 'Colombo 05',
      district: 'Colombo',
      postalCode: '00500',
      isDefault: true,
      createdAt: new Date(Date.now() - 86400000 * 10).toISOString(),
    },
    {
      id: 'addr-002',
      customerId: this.defaultCustomerId,
      label: 'Office',
      line1: '25 Dharmapala Mawatha',
      line2: 'Level 6, Tech Tower',
      city: 'Colombo 07',
      district: 'Colombo',
      postalCode: '00700',
      isDefault: false,
      createdAt: new Date(Date.now() - 86400000 * 8).toISOString(),
    },
    {
      id: 'addr-003',
      customerId: this.defaultCustomerId,
      label: 'Parents House',
      line1: '88 Kandy Road',
      line2: null,
      city: 'Kadugannawa',
      district: 'Kandy',
      postalCode: '20300',
      isDefault: false,
      createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
    },
    {
      id: 'addr-004',
      customerId: this.defaultCustomerId,
      label: 'Galle Branch',
      line1: '12 Fort Rampart Street',
      line2: null,
      city: 'Galle',
      district: 'Galle',
      postalCode: '80000',
      isDefault: false,
      createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    },
  ]);

  private parcels = signal<CustomerParcel[]>([
    {
      id: 'pcl-cust-001',
      trackingNumber: 'SD-20261027-H9K3N1',
      status: 'IN_TRANSIT',
      pickupAddress: {
        line1: '142 Havelock Road',
        line2: 'Apt 4B',
        city: 'Colombo 05',
        district: 'Colombo',
        postalCode: '00500',
      },
      dropoffAddress: {
        line1: '88 Kandy Road',
        line2: null,
        city: 'Kadugannawa',
        district: 'Kandy',
        postalCode: '20300',
      },
      weightKg: 2.5,
      size: 'MEDIUM',
      fee: 750.0,
      attemptCount: 0,
      recipientName: 'Sunil Gunawardena',
      recipientPhone: '+94771234567',
      notes: 'Call before delivery',
      createdAt: new Date(Date.now() - 3600000 * 5).toISOString(),
    },
    {
      id: 'pcl-cust-002',
      trackingNumber: 'SD-20261026-F4P8W2',
      status: 'DELIVERED',
      pickupAddress: {
        line1: '25 Dharmapala Mawatha',
        line2: null,
        city: 'Colombo 07',
        district: 'Colombo',
        postalCode: '00700',
      },
      dropoffAddress: {
        line1: '12 Fort Rampart Street',
        line2: null,
        city: 'Galle',
        district: 'Galle',
        postalCode: '80000',
      },
      weightKg: 1.0,
      size: 'SMALL',
      fee: 500.0,
      attemptCount: 1,
      recipientName: 'Anoma Silva',
      recipientPhone: '+94719876543',
      notes: null,
      createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    },
    {
      id: 'pcl-cust-003',
      trackingNumber: 'SD-20261028-B7R1Q9',
      status: 'CREATED',
      pickupAddress: {
        line1: '142 Havelock Road',
        line2: null,
        city: 'Colombo 05',
        district: 'Colombo',
        postalCode: '00500',
      },
      dropoffAddress: {
        line1: '45 Main Street',
        line2: null,
        city: 'Kurunegala',
        district: 'Kurunegala',
        postalCode: '60000',
      },
      weightKg: 4.2,
      size: 'LARGE',
      fee: 1070.0,
      attemptCount: 0,
      recipientName: 'Ruwan Wijesinghe',
      recipientPhone: '+94762233445',
      notes: 'Deliver to security desk if absent',
      createdAt: new Date(Date.now() - 1800000).toISOString(),
    },
  ]);

  // =========================================================================
  // Address Book Operations
  // =========================================================================

  listAddresses(): Observable<CustomerAddress[]> {
    return of([...this.addresses()]);
  }

  createAddress(
    addressData: Omit<CustomerAddress, 'id' | 'customerId' | 'createdAt'>
  ): Observable<CustomerAddress> {
    const isFirst = this.addresses().length === 0;
    const shouldBeDefault = addressData.isDefault || isFirst;

    let updated = this.addresses();
    if (shouldBeDefault) {
      updated = updated.map((a) => ({ ...a, isDefault: false }));
    }

    const newAddress: CustomerAddress = {
      ...addressData,
      id: 'addr-' + Math.random().toString(36).substring(2, 9),
      customerId: this.defaultCustomerId,
      isDefault: shouldBeDefault,
      createdAt: new Date().toISOString(),
    };

    this.addresses.set([newAddress, ...updated]);
    return of(newAddress);
  }

  updateAddress(
    id: string,
    changes: Partial<CustomerAddress>
  ): Observable<CustomerAddress> {
    const current = this.addresses();
    const idx = current.findIndex((a) => a.id === id);
    if (idx === -1) {
      throw new Error(`Address not found with id: ${id}`);
    }

    let updatedList = [...current];
    if (changes.isDefault) {
      updatedList = updatedList.map((a) => ({ ...a, isDefault: false }));
    }

    const updated = { ...updatedList[idx], ...changes };
    updatedList[idx] = updated;
    this.addresses.set(updatedList);
    return of(updated);
  }

  deleteAddress(id: string): Observable<void> {
    const current = this.addresses();
    const filtered = current.filter((a) => a.id !== id);
    if (filtered.length > 0 && !filtered.some((a) => a.isDefault)) {
      filtered[0].isDefault = true;
    }
    this.addresses.set(filtered);
    return of(undefined);
  }

  setDefaultAddress(id: string): Observable<CustomerAddress> {
    const current = this.addresses();
    const idx = current.findIndex((a) => a.id === id);
    if (idx === -1) {
      throw new Error(`Address not found with id: ${id}`);
    }

    const updated = current.map((a) => ({
      ...a,
      isDefault: a.id === id,
    }));
    this.addresses.set(updated);
    return of(updated[idx]);
  }

  // =========================================================================
  // Fee Calculation & Quotes
  // =========================================================================

  calculateFeePreview(
    pickupDistrict: District,
    dropoffDistrict: District,
    weightKg: number,
    size: ParcelSize
  ): FeePreview {
    const baseFee = 350.0;
    const interDistrictFee = pickupDistrict !== dropoffDistrict ? 150.0 : 0.0;
    const weightFee = weightKg > 1.0 ? Math.ceil(weightKg - 1.0) * 100.0 : 0.0;
    const sizeTierFee = size === 'LARGE' ? 250.0 : size === 'MEDIUM' ? 100.0 : 0.0;
    const totalFee = baseFee + interDistrictFee + weightFee + sizeTierFee;

    return {
      baseFee,
      interDistrictFee,
      weightFee,
      sizeTierFee,
      totalFee,
      pickupDistrict,
      dropoffDistrict,
      weightKg,
      size,
    };
  }

  // =========================================================================
  // Parcel Operations
  // =========================================================================

  listParcels(statusFilter?: ParcelStatus): Observable<CustomerParcel[]> {
    let list = this.parcels();
    if (statusFilter) {
      list = list.filter((p) => p.status === statusFilter);
    }
    return of([...list]);
  }

  getParcel(id: string): Observable<CustomerParcel | undefined> {
    const found = this.parcels().find((p) => p.id === id);
    return of(found ? { ...found } : undefined);
  }

  createParcel(
    request: CreateCustomerParcelRequest
  ): Observable<CustomerParcel> {
    const quote = this.calculateFeePreview(
      request.pickupAddress.district,
      request.dropoffAddress.district,
      request.weightKg,
      request.size
    );

    const now = new Date();
    const yyyymmdd = now.toISOString().slice(0, 10).replace(/-/g, '');
    const randSuffix = Math.random().toString(36).substring(2, 8).toUpperCase();
    const trackingNumber = `SD-${yyyymmdd}-${randSuffix}`;

    const newParcel: CustomerParcel = {
      id: 'pcl-' + Math.random().toString(36).substring(2, 9),
      trackingNumber,
      status: 'CREATED',
      pickupAddress: { ...request.pickupAddress },
      dropoffAddress: { ...request.dropoffAddress },
      weightKg: Number(request.weightKg),
      size: request.size,
      fee: quote.totalFee,
      attemptCount: 0,
      recipientName: request.recipientName.trim(),
      recipientPhone: request.recipientPhone.trim(),
      notes: request.notes?.trim() || null,
      createdAt: now.toISOString(),
      updatedAt: now.toISOString(),
    };

    this.parcels.set([newParcel, ...this.parcels()]);
    return of(newParcel);
  }

  cancelParcel(id: string, reason?: string): Observable<CustomerParcel> {
    const current = this.parcels();
    const idx = current.findIndex((p) => p.id === id);
    if (idx === -1) {
      throw new Error(`Parcel not found with id: ${id}`);
    }
    if (current[idx].status !== 'CREATED') {
      throw new Error(
        `Cannot cancel parcel in status ${current[idx].status}. Allowed only while CREATED.`
      );
    }

    const updated = {
      ...current[idx],
      status: 'CANCELLED' as ParcelStatus,
      failureReason: reason || 'Cancelled by customer',
      updatedAt: new Date().toISOString(),
    };

    const updatedList = [...current];
    updatedList[idx] = updated;
    this.parcels.set(updatedList);
    return of(updated);
  }
}
