export type ParcelLifecycleStatus =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'CANCELLED';

export type WebhookDeliveryStatus =
  | 'SUCCESS'
  | 'PENDING_RETRY'
  | 'FAILED'
  | 'CANCELLED'
  | 'PENDING';

export interface MerchantOrder {
  orderId: string;
  parcelId: string;
  trackingNumber: string;
  status: ParcelLifecycleStatus;
  recipientName?: string | null;
  recipientAddress?: string | null;
  fee?: number | null;
  weightKg?: number | null;
  createdAt: string;
  updatedAt?: string | null;
  merchantId?: string;
  externalReference?: string;
}

export interface MerchantOrderListPage {
  content: MerchantOrder[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface MerchantOrderFilterParams {
  status?: string;
  trackingNumber?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface MerchantApiCredentials {
  merchantId: string;
  status: 'PENDING' | 'APPROVED';
  clientId: string | null;
  clientSecret: string | null;
  secretAvailableOnce: boolean;
}

export interface OrderCounts {
  total: number;
  created: number;
  pickedUp?: number;
  inTransit: number;
  outForDelivery?: number;
  delivered: number;
  failed: number;
  cancelled: number;
}

export interface WebhookSettings {
  merchantId: string;
  url: string | null;
  active: boolean;
  subscribedEvents: string[];
  secretConfigured: boolean;
  secretPreview: string | null;
  rawSecret?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeliveryAttempt {
  attemptNumber: number;
  attemptedAt: string;
  httpStatusCode?: number;
  latencyMs: number;
  success: boolean;
  errorMessage?: string | null;
}

export interface WebhookDeliveryItem {
  deliveryId: string;
  eventId: string;
  eventType: string;
  trackingNumber: string;
  targetUrl: string;
  status: WebhookDeliveryStatus;
  httpStatusCode?: number;
  attempts: number;
  lastAttemptAt?: string;
  nextRetryAt?: string | null;
  errorSummary?: string | null;
  createdAt: string;
  requestHeaders?: Record<string, string>;
  payloadJson?: string;
  responseHeaders?: Record<string, string>;
  responseBody?: string;
  retryHistory?: DeliveryAttempt[];
}

export interface WebhookDeliveryPage {
  content: WebhookDeliveryItem[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface WebhookDeliveryDetail {
  deliveryId: string;
  eventId: string;
  eventType: string;
  status: WebhookDeliveryStatus;
  targetUrl: string;
  attempts: number;
  httpStatusCode: number | null;
  requestHeaders: Record<string, string> | null;
  payload: unknown;
  responseHeaders: Record<string, string> | null;
  responseBody: string | null;
  lastAttemptAt: string | null;
  nextRetryAt: string | null;
  retryHistory: DeliveryAttempt[];
  createdAt: string;
}

export interface TestWebhookResult {
  success: boolean;
  statusCode: number;
  latencyMs: number;
  attemptedAt: string;
  targetUrl: string;
  signatureSent: string;
  timestampSent: string;
  responseBodySummary?: string;
  errorMessage?: string | null;
}
