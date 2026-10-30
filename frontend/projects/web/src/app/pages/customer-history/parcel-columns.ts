import { TableColumn } from 'ui';
import { CustomerParcel } from './customer-history.model';

/** Columns shared by the dashboard's recent list and the history table. */
export const PARCEL_COLUMNS: TableColumn<CustomerParcel>[] = [
  { key: 'trackingNumber', header: 'Tracking no.' },
  { key: 'status', header: 'Status' },
  { key: 'dropoff', header: 'Deliver to', value: (p) => `${p.dropoffAddress.city}, ${p.dropoffAddress.district}` },
  { key: 'recipientName', header: 'Recipient' },
  { key: 'createdAt', header: 'Created' },
  { key: 'fee', header: 'Fee', align: 'right' },
];

export const parcelKey = (parcel: CustomerParcel): string => parcel.id;
