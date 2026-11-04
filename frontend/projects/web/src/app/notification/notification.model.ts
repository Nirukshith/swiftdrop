/**
 * Notification domain models aligned with GET /api/v1/notifications contract
 * in contracts/openapi/integration.yaml.
 */

export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';

export interface NotificationDto {
  id: string;
  title: string;
  message: string;
  channel: NotificationChannel;
  parcelId?: string | null;
  trackingNumber?: string | null;
  read: boolean;
  createdAt: string;
}

export type NotificationItem = NotificationDto;

export interface NotificationPageResponse {
  items: NotificationDto[];
  totalElements: number;
  unreadCount: number;
}

