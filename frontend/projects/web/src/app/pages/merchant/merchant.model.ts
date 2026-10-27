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
  merchantId: string;
  orderId: string;
  parcelId: string;
  trackingNumber: string;
  externalReference?: string;
  status: ParcelLifecycleStatus;
  recipientName: string;
  recipientAddress: string;
  weightKg?: number;
  createdAt: string;
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
  inTransit: number;
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
