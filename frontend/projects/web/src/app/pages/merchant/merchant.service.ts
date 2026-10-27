import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  MerchantOrder,
  MerchantApiCredentials,
  OrderCounts,
  TestWebhookResult,
  WebhookDeliveryItem,
  WebhookSettings,
} from './merchant.model';

@Injectable({
  providedIn: 'root',
})
export class MerchantService {
  private readonly defaultMerchantId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d';
  private mockClientSecretAvailable = true;

  private orders = signal<MerchantOrder[]>([
    {
      orderId: 'ord-802101',
      merchantId: this.defaultMerchantId,
      parcelId: 'pcl-55102',
      trackingNumber: 'SD-20261027-802101',
      externalReference: 'PO-2026-904',
      status: 'IN_TRANSIT',
      recipientName: 'Alice Johnson',
      recipientAddress: '42 Galle Road, Colombo 03',
      weightKg: 2.4,
      createdAt: new Date(Date.now() - 3600000 * 4).toISOString(),
    },
    {
      orderId: 'ord-802102',
      merchantId: this.defaultMerchantId,
      parcelId: 'pcl-55103',
      trackingNumber: 'SD-20261027-802102',
      externalReference: 'PO-2026-905',
      status: 'OUT_FOR_DELIVERY',
      recipientName: 'Kasun Perera',
      recipientAddress: '15 Kandy Road, Kiribathgoda',
      weightKg: 0.8,
      createdAt: new Date(Date.now() - 3600000 * 6).toISOString(),
    },
    {
      orderId: 'ord-802103',
      merchantId: this.defaultMerchantId,
      parcelId: 'pcl-55104',
      trackingNumber: 'SD-20261027-802103',
      externalReference: 'PO-2026-906',
      status: 'DELIVERED',
      recipientName: 'Fathima Rizwan',
      recipientAddress: '88 Main Street, Negombo',
      weightKg: 5.1,
      createdAt: new Date(Date.now() - 3600000 * 24).toISOString(),
    },
    {
      orderId: 'ord-802104',
      merchantId: this.defaultMerchantId,
      parcelId: 'pcl-55105',
      trackingNumber: 'SD-20261026-802104',
      externalReference: 'PO-2026-907',
      status: 'DELIVERED',
      recipientName: 'Nimal Fernando',
      recipientAddress: '10 Flower Road, Colombo 07',
      weightKg: 1.2,
      createdAt: new Date(Date.now() - 3600000 * 30).toISOString(),
    },
    {
      orderId: 'ord-802105',
      merchantId: this.defaultMerchantId,
      parcelId: 'pcl-55106',
      trackingNumber: 'SD-20261025-802105',
      externalReference: 'PO-2026-908',
      status: 'FAILED',
      recipientName: 'Dinesh Silva',
      recipientAddress: '23 Beach Road, Mount Lavinia',
      weightKg: 3.5,
      createdAt: new Date(Date.now() - 3600000 * 48).toISOString(),
    },
    {
      orderId: 'ord-802106',
      merchantId: this.defaultMerchantId,
      parcelId: 'pcl-55107',
      trackingNumber: 'SD-20261025-802106',
      externalReference: 'PO-2026-909',
      status: 'CREATED',
      recipientName: 'Sunil Wickramasinghe',
      recipientAddress: '104 High Level Road, Nugegoda',
      weightKg: 1.0,
      createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
    },
    {
      orderId: 'ord-other-merchant-1',
      merchantId: 'a2fd0a58-24fc-4e93-9862-02a20dd7b461',
      parcelId: 'pcl-other-merchant-1',
      trackingNumber: 'SD-20261027-OTHER1',
      externalReference: 'OTHER-STORE-01',
      status: 'CREATED',
      recipientName: 'Private sample recipient',
      recipientAddress: '1 Other Road, Colombo',
      weightKg: 0.5,
      createdAt: new Date(Date.now() - 3600000).toISOString(),
    },
  ]);

  private settings = signal<WebhookSettings>({
    merchantId: this.defaultMerchantId,
    url: 'https://api.partnerstore.com/webhooks/swiftdrop',
    active: true,
    subscribedEvents: ['PARCEL_CREATED', 'PARCEL_STATUS_CHANGED'],
    secretConfigured: true,
    secretPreview: 'whsec_••••••••••••••••3f8a',
    rawSecret: null,
    createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
    updatedAt: new Date(Date.now() - 3600000).toISOString(),
  });

  private deliveries = signal<WebhookDeliveryItem[]>([
    {
      deliveryId: 'c1a8e9f2-2b3c-4d5e-8f9a-0b1c2d3e4f5a',
      eventId: '7f8e3b21-4a5c-6d7e-8f9a-0b1c2d3e4f5a',
      eventType: 'PARCEL_STATUS_CHANGED',
      trackingNumber: 'SD-20261027-802102',
      targetUrl: 'https://api.partnerstore.com/webhooks/swiftdrop',
      status: 'SUCCESS',
      httpStatusCode: 200,
      attempts: 1,
      lastAttemptAt: new Date(Date.now() - 3600000 * 2).toISOString(),
      nextRetryAt: null,
      errorSummary: null,
      createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
      requestHeaders: {
        'Content-Type': 'application/json',
        'X-SwiftDrop-Signature': 't=1729864800,v1=9f8a3c2b1e0d4a5b6c7d8e9f0a1b2c3d',
        'X-SwiftDrop-Timestamp': '1729864800',
        'X-SwiftDrop-Delivery-Id': 'c1a8e9f2-2b3c-4d5e-8f9a-0b1c2d3e4f5a',
      },
      payloadJson: JSON.stringify(
        {
          deliveryId: 'c1a8e9f2-2b3c-4d5e-8f9a-0b1c2d3e4f5a',
          eventId: '7f8e3b21-4a5c-6d7e-8f9a-0b1c2d3e4f5a',
          eventType: 'PARCEL_STATUS_CHANGED',
          sequence: 3,
          occurredAt: new Date(Date.now() - 3600000 * 2).toISOString(),
          merchantId: this.defaultMerchantId,
          parcel: {
            parcelId: 'pcl-55103',
            trackingNumber: 'SD-20261027-802102',
            status: 'OUT_FOR_DELIVERY',
            fromStatus: 'IN_TRANSIT',
            toStatus: 'OUT_FOR_DELIVERY',
          },
        },
        null,
        2
      ),
      responseHeaders: {
        'content-type': 'application/json',
        server: 'nginx/1.24.0',
      },
      responseBody: '{"received":true,"status":"acknowledged"}',
      retryHistory: [
        {
          attemptNumber: 1,
          attemptedAt: new Date(Date.now() - 3600000 * 2).toISOString(),
          httpStatusCode: 200,
          latencyMs: 142,
          success: true,
          errorMessage: null,
        },
      ],
    },
    {
      deliveryId: 'd2b9f0a3-3c4d-5e6f-9a0b-1c2d3e4f5a6b',
      eventId: '8a9f4c32-5b6d-7e8f-9a0b-1c2d3e4f5a6b',
      eventType: 'PARCEL_STATUS_CHANGED',
      trackingNumber: 'SD-20261025-802105',
      targetUrl: 'https://api.partnerstore.com/webhooks/swiftdrop',
      status: 'FAILED',
      httpStatusCode: 504,
      attempts: 2,
      lastAttemptAt: new Date(Date.now() - 3600000 * 12).toISOString(),
      nextRetryAt: null,
      errorSummary: 'Gateway Timeout: merchant server did not respond within 10s',
      createdAt: new Date(Date.now() - 3600000 * 14).toISOString(),
      requestHeaders: {
        'Content-Type': 'application/json',
        'X-SwiftDrop-Signature': 't=1729828800,v1=4d7f508a54c123456789abcdef012345',
        'X-SwiftDrop-Timestamp': '1729828800',
        'X-SwiftDrop-Delivery-Id': 'd2b9f0a3-3c4d-5e6f-9a0b-1c2d3e4f5a6b',
      },
      payloadJson: JSON.stringify(
        {
          deliveryId: 'd2b9f0a3-3c4d-5e6f-9a0b-1c2d3e4f5a6b',
          eventId: '8a9f4c32-5b6d-7e8f-9a0b-1c2d3e4f5a6b',
          eventType: 'PARCEL_STATUS_CHANGED',
          sequence: 4,
          occurredAt: new Date(Date.now() - 3600000 * 14).toISOString(),
          merchantId: this.defaultMerchantId,
          parcel: {
            parcelId: 'pcl-55106',
            trackingNumber: 'SD-20261025-802105',
            status: 'FAILED',
            fromStatus: 'OUT_FOR_DELIVERY',
            toStatus: 'FAILED',
            reason: 'Recipient unavailable after 3 attempts',
          },
        },
        null,
        2
      ),
      responseHeaders: {
        'content-type': 'text/plain',
      },
      responseBody: 'Gateway Timeout: connection reset by peer',
      retryHistory: [
        {
          attemptNumber: 1,
          attemptedAt: new Date(Date.now() - 3600000 * 14).toISOString(),
          httpStatusCode: 504,
          latencyMs: 10005,
          success: false,
          errorMessage: 'Gateway Timeout',
        },
        {
          attemptNumber: 2,
          attemptedAt: new Date(Date.now() - 3600000 * 12).toISOString(),
          httpStatusCode: 504,
          latencyMs: 10002,
          success: false,
          errorMessage: 'Gateway Timeout',
        },
      ],
    },
  ]);

  getOrders(statusFilter?: string, search?: string): Observable<MerchantOrder[]> {
    let list = this.orders().filter((order) => order.merchantId === this.defaultMerchantId);
    if (statusFilter && statusFilter !== 'ALL') {
      list = list.filter((o) => o.status === statusFilter);
    }
    if (search && search.trim().length > 0) {
      const q = search.trim().toLowerCase();
      list = list.filter(
        (o) =>
          o.trackingNumber.toLowerCase().includes(q) ||
          o.recipientName.toLowerCase().includes(q) ||
          (o.externalReference && o.externalReference.toLowerCase().includes(q))
      );
    }
    return of(list);
  }

  getOrderCounts(): Observable<OrderCounts> {
    const list = this.orders().filter((order) => order.merchantId === this.defaultMerchantId);
    const counts: OrderCounts = {
      total: list.length,
      created: list.filter((o) => o.status === 'CREATED').length,
      inTransit: list.filter(
        (o) =>
          o.status === 'IN_TRANSIT' ||
          o.status === 'PICKED_UP' ||
          o.status === 'OUT_FOR_DELIVERY'
      ).length,
      delivered: list.filter((o) => o.status === 'DELIVERED').length,
      failed: list.filter((o) => o.status === 'FAILED').length,
      cancelled: list.filter((o) => o.status === 'CANCELLED').length,
    };
    return of(counts);
  }

  getApiCredentials(): Observable<MerchantApiCredentials> {
    const clientSecret = this.mockClientSecretAvailable ? 'swd_test_secret_once_7f3a9c2e' : null;
    this.mockClientSecretAvailable = false;
    return of({
      merchantId: this.defaultMerchantId,
      status: 'APPROVED',
      clientId: 'swd_test_client_merchant_01',
      clientSecret,
      secretAvailableOnce: clientSecret !== null,
    });
  }

  getWebhookSettings(): Observable<WebhookSettings> {
    return of(this.settings());
  }

  updateWebhookSettings(
    url: string,
    active: boolean,
    subscribedEvents: string[],
    regenerateSecret: boolean
  ): Observable<WebhookSettings> {
    const current = this.settings();
    let newSecretPreview = current.secretPreview;
    let newRawSecret: string | null = null;

    if (regenerateSecret || !current.secretConfigured) {
      const randomHex = Math.random().toString(16).substring(2, 6);
      newRawSecret = 'whsec_' + Math.random().toString(36).substring(2, 18) + randomHex;
      newSecretPreview = 'whsec_••••••••••••••••' + randomHex;
    }

    const updated: WebhookSettings = {
      ...current,
      url,
      active,
      subscribedEvents,
      secretConfigured: true,
      secretPreview: newSecretPreview,
      rawSecret: newRawSecret,
      updatedAt: new Date().toISOString(),
    };

    this.settings.set(updated);
    return of(updated);
  }

  triggerTestWebhook(targetUrlOverride?: string): Observable<TestWebhookResult> {
    const s = this.settings();
    const url = targetUrlOverride?.trim() || s.url || 'https://api.partnerstore.com/webhooks/swiftdrop';
    const epochSec = Math.floor(Date.now() / 1000);

    const result: TestWebhookResult = {
      success: true,
      statusCode: 200,
      latencyMs: 98,
      attemptedAt: new Date().toISOString(),
      targetUrl: url,
      signatureSent: `t=${epochSec},v1=4d7f508a54c3e2109876abcdef543210`,
      timestampSent: String(epochSec),
      responseBodySummary: '{"status":"ok","message":"Test event verified successfully"}',
      errorMessage: null,
    };

    // Add an entry to deliveries
    const newLog: WebhookDeliveryItem = {
      deliveryId: 'test-' + Math.random().toString(36).substring(2, 10),
      eventId: 'evt-' + Math.random().toString(36).substring(2, 10),
      eventType: 'TEST_EVENT',
      trackingNumber: 'SD-TEST-' + epochSec,
      targetUrl: url,
      status: 'SUCCESS',
      httpStatusCode: 200,
      attempts: 1,
      lastAttemptAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      requestHeaders: {
        'Content-Type': 'application/json',
        'X-SwiftDrop-Signature': result.signatureSent,
        'X-SwiftDrop-Timestamp': result.timestampSent,
      },
      payloadJson: JSON.stringify(
        {
          eventType: 'TEST_EVENT',
          timestamp: epochSec,
          targetUrl: url,
          message: 'SwiftDrop Webhook Test Event',
        },
        null,
        2
      ),
      responseBody: result.responseBodySummary,
      retryHistory: [
        {
          attemptNumber: 1,
          attemptedAt: new Date().toISOString(),
          httpStatusCode: 200,
          latencyMs: 98,
          success: true,
        },
      ],
    };

    this.deliveries.update((prev) => [newLog, ...prev]);
    return of(result).pipe(delay(200));
  }

  getDeliveryLogs(statusFilter?: string): Observable<WebhookDeliveryItem[]> {
    let list = this.deliveries();
    if (statusFilter && statusFilter !== 'ALL') {
      list = list.filter((d) => d.status === statusFilter);
    }
    return of(list);
  }

  getDeliveryDetail(deliveryId: string): Observable<WebhookDeliveryItem | undefined> {
    const found = this.deliveries().find((d) => d.deliveryId === deliveryId);
    return of(found);
  }

  retryDelivery(deliveryId: string): Observable<WebhookDeliveryItem> {
    const list = this.deliveries();
    const targetIndex = list.findIndex((d) => d.deliveryId === deliveryId);

    if (targetIndex === -1) {
      throw new Error('Delivery not found');
    }

    const current = list[targetIndex];
    const newAttempts = current.attempts + 1;
    const updated: WebhookDeliveryItem = {
      ...current,
      attempts: newAttempts,
      status: 'SUCCESS',
      httpStatusCode: 200,
      errorSummary: null,
      lastAttemptAt: new Date().toISOString(),
      retryHistory: [
        ...(current.retryHistory || []),
        {
          attemptNumber: newAttempts,
          attemptedAt: new Date().toISOString(),
          httpStatusCode: 200,
          latencyMs: 115,
          success: true,
        },
      ],
    };

    const updatedList = [...list];
    updatedList[targetIndex] = updated;
    this.deliveries.set(updatedList);

    return of(updated).pipe(delay(150));
  }
}
