import { CustomerParcel, ParcelPage, ParcelStatus } from './customer-history.model';

/** Test data for the customer history specs. */
export function parcel(n: number, status: ParcelStatus = 'IN_TRANSIT'): CustomerParcel {
  return {
    id: `p-${n}`,
    trackingNumber: `SD-20261028-TST${String(n).padStart(3, '0')}`,
    status,
    pickupAddress: { line1: '1 Main St', city: 'Colombo 03', district: 'Colombo', postalCode: '00300' },
    dropoffAddress: { line1: '2 Hill St', city: 'Kandy', district: 'Kandy', postalCode: '20000' },
    recipientName: `Recipient ${n}`,
    fee: 750,
    createdAt: '2026-10-28T08:00:00Z',
    updatedAt: '2026-10-28T09:00:00Z',
  };
}

export function pageOf(content: CustomerParcel[], totalElements = content.length, page = 0, size = 20): ParcelPage {
  return { content, totalElements, totalPages: Math.ceil(totalElements / size), page, size };
}
