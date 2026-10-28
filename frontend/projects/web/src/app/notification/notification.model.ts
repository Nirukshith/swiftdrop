/**
 * Notification domain models aligned with GET /api/v1/notifications contract
 * in contracts/openapi/integration.yaml.
 */

export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';

export interface NotificationItem {
  id: string;
  userId: string;
  eventId: string;
  channel: NotificationChannel;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  content: NotificationItem[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

