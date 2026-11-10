import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  MerchantOrderFilterParams,
  MerchantOrderListPage,
  OrderCounts,
  TestWebhookResult,
  WebhookDeliveryItem,
  WebhookSettings,
} from './merchant.model';

@Injectable({
  providedIn: 'root',
})
export class MerchantService {
  private readonly http = inject(HttpClient);
  private readonly defaultMerchantId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d';

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

  getOrders(
    paramsOrStatus?: MerchantOrderFilterParams | string,
    search?: string
  ): Observable<MerchantOrderListPage> {
    let params = new HttpParams();

    if (typeof paramsOrStatus === 'object' && paramsOrStatus !== null) {
      if (paramsOrStatus.status && paramsOrStatus.status !== 'ALL') {
        params = params.set('status', paramsOrStatus.status);
      }
      if (paramsOrStatus.trackingNumber && paramsOrStatus.trackingNumber.trim()) {
        params = params.set('trackingNumber', paramsOrStatus.trackingNumber.trim());
      }
      if (paramsOrStatus.from && paramsOrStatus.from.trim()) {
        params = params.set('from', paramsOrStatus.from.trim());
      }
      if (paramsOrStatus.to && paramsOrStatus.to.trim()) {
        params = params.set('to', paramsOrStatus.to.trim());
      }
      if (paramsOrStatus.page !== undefined && paramsOrStatus.page !== null) {
        params = params.set('page', paramsOrStatus.page.toString());
      }
      if (paramsOrStatus.size !== undefined && paramsOrStatus.size !== null) {
        params = params.set('size', paramsOrStatus.size.toString());
      }
    } else if (typeof paramsOrStatus === 'string') {
      if (paramsOrStatus !== 'ALL') {
        params = params.set('status', paramsOrStatus);
      }
      if (search && search.trim()) {
        params = params.set('trackingNumber', search.trim());
      }
    }

    return this.http.get<MerchantOrderListPage>('/api/v1/merchant/orders', { params });
  }

  getOrderCounts(): Observable<OrderCounts> {
    return this.http.get<OrderCounts>('/api/v1/merchant/orders/counts');
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
