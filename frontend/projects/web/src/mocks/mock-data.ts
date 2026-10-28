import { ParcelHistoryEntry, ParcelResponse, ParcelStatus, SavedAddressResponse } from './mock-models';

/** Matches MOCK_USERS.customer in shared-auth, so mock login and mock data line up. */
export const MOCK_CUSTOMER_ID = 'usr-mock-customer-1';

const COLOMBO_PICKUP = { line1: '45 Galle Road', line2: 'Floor 3, Apt 3B', city: 'Colombo 03', district: 'Colombo', postalCode: '00300' };

interface Seed {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  recipientName: string;
  dropoff: { line1: string; city: string; district: string; postalCode: string };
  weightKg: number;
  size: ParcelResponse['size'];
  fee: number;
  /** Statuses after CREATED, in order. */
  path: ParcelStatus[];
  createdAt: string;
  failureReason?: string;
}

const SEEDS: Seed[] = [
  {
    id: '7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11',
    trackingNumber: 'SD-20261026-A1B2C3',
    status: 'OUT_FOR_DELIVERY',
    recipientName: 'Sunil Silva',
    dropoff: { line1: '12 Temple Road', city: 'Kandy', district: 'Kandy', postalCode: '20000' },
    weightKg: 2.5,
    size: 'MEDIUM',
    fee: 750,
    path: ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'],
    createdAt: '2026-10-26T08:00:00Z',
  },
  {
    id: '1b6f0c3e-2a44-4d9b-9f7e-0c1d2e3f4a51',
    trackingNumber: 'SD-20261024-K7M2Q9',
    status: 'DELIVERED',
    recipientName: 'Nimal Perera',
    dropoff: { line1: '8 Lighthouse Street', city: 'Galle Fort', district: 'Galle', postalCode: '80000' },
    weightKg: 1.2,
    size: 'SMALL',
    fee: 650,
    path: ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'],
    createdAt: '2026-10-24T09:15:00Z',
  },
  {
    id: '2c7a1d4f-3b55-4eac-8a8f-1d2e3f4a5b62',
    trackingNumber: 'SD-20261027-P4X8Z1',
    status: 'CREATED',
    recipientName: 'Kavindu Jayasuriya',
    dropoff: { line1: '21 Station Road', city: 'Gampaha', district: 'Gampaha', postalCode: '11000' },
    weightKg: 4,
    size: 'LARGE',
    fee: 900,
    path: [],
    createdAt: '2026-10-27T11:30:00Z',
  },
  {
    id: '3d8b2e5a-4c66-4fbd-9b9a-2e3f4a5b6c73',
    trackingNumber: 'SD-20261025-R3T6Y2',
    status: 'FAILED',
    recipientName: 'Amaya Fernando',
    dropoff: { line1: '3 Beach Road', city: 'Matara', district: 'Matara', postalCode: '81000' },
    weightKg: 0.8,
    size: 'SMALL',
    fee: 700,
    path: ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'FAILED'],
    createdAt: '2026-10-25T07:45:00Z',
    failureReason: 'RECIPIENT_UNAVAILABLE',
  },
  {
    id: '4e9c3f6b-5d77-40ce-8cab-3f4a5b6c7d84',
    trackingNumber: 'SD-20261023-H5J9L3',
    status: 'CANCELLED',
    recipientName: 'Ruwan Wickramasinghe',
    dropoff: { line1: '77 Main Street', city: 'Jaffna', district: 'Jaffna', postalCode: '40000' },
    weightKg: 3,
    size: 'MEDIUM',
    fee: 1200,
    path: ['CANCELLED'],
    createdAt: '2026-10-23T14:00:00Z',
  },
  {
    id: '5fad4a7c-6e88-41df-9dbc-4a5b6c7d8e95',
    trackingNumber: 'SD-20261027-W2E4R6',
    status: 'IN_TRANSIT',
    recipientName: 'Dilani Rajapaksa',
    dropoff: { line1: '5 Lake Drive', city: 'Kurunegala', district: 'Kurunegala', postalCode: '60000' },
    weightKg: 6.5,
    size: 'LARGE',
    fee: 1100,
    path: ['PICKED_UP', 'IN_TRANSIT'],
    createdAt: '2026-10-27T06:20:00Z',
  },
];

function hoursAfter(iso: string, hours: number): string {
  return new Date(new Date(iso).getTime() + hours * 3_600_000).toISOString().replace('.000Z', 'Z');
}

/** Builds fresh seed parcels and their status history. Called on start and by reset(). */
export function buildSeedData(): { parcels: ParcelResponse[]; history: Map<string, ParcelHistoryEntry[]> } {
  const parcels: ParcelResponse[] = [];
  const history = new Map<string, ParcelHistoryEntry[]>();

  for (const seed of SEEDS) {
    const entries: ParcelHistoryEntry[] = [{ sequence: 1, fromStatus: null, toStatus: 'CREATED', occurredAt: seed.createdAt }];
    let previous: ParcelStatus = 'CREATED';
    // Sequence 2 is the courier assignment (no status change), so status changes start at 3.
    seed.path.forEach((status, index) => {
      entries.push({
        sequence: index + 3,
        fromStatus: previous,
        toStatus: status,
        occurredAt: hoursAfter(seed.createdAt, (index + 1) * 3),
        reason: status === 'FAILED' ? seed.failureReason ?? null : status === 'CANCELLED' ? 'Customer placed duplicate order' : null,
      });
      previous = status;
    });
    history.set(seed.id, entries);

    const last = entries[entries.length - 1];
    parcels.push({
      id: seed.id,
      trackingNumber: seed.trackingNumber,
      status: seed.status,
      customerId: MOCK_CUSTOMER_ID,
      merchantId: null,
      courierId: seed.path.length && seed.status !== 'CANCELLED' ? '9a21b34c-d102-4c28-98e1-51829e4a012b' : null,
      pickupAddress: { ...COLOMBO_PICKUP },
      dropoffAddress: { ...seed.dropoff, line2: null },
      weightKg: seed.weightKg,
      size: seed.size,
      fee: seed.fee,
      attemptCount: 0,
      failureReason: seed.failureReason ?? null,
      recipientName: seed.recipientName,
      recipientPhone: '+94771234567',
      notes: null,
      proof: seed.status === 'DELIVERED' ? { recipientName: seed.recipientName, proofPhotoPath: null } : null,
      sequence: last.sequence ?? 1,
      createdAt: seed.createdAt,
      updatedAt: last.occurredAt,
    });
  }
  return { parcels, history };
}

export function buildSeedAddresses(): SavedAddressResponse[] {
  return [
    {
      id: '3f829a1b-4c28-4981-8912-10a294819cde',
      customerId: MOCK_CUSTOMER_ID,
      label: 'Home',
      ...COLOMBO_PICKUP,
      isDefault: true,
      createdAt: '2026-10-20T10:00:00Z',
      updatedAt: null,
    },
    {
      id: '6a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d',
      customerId: MOCK_CUSTOMER_ID,
      label: 'Office',
      line1: '100 Union Place',
      line2: null,
      city: 'Colombo 02',
      district: 'Colombo',
      postalCode: '00200',
      isDefault: false,
      createdAt: '2026-10-21T10:00:00Z',
      updatedAt: null,
    },
  ];
}
