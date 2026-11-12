import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  MerchantOrderFilterParams,
  MerchantOrderListPage,
  OrderCounts,
  TestWebhookResult,
  WebhookDeliveryDetail,
  WebhookDeliveryPage,
  WebhookSettings,
} from './merchant.model';

@Injectable({ providedIn: 'root' })
export class MerchantService {
  private readonly http = inject(HttpClient);
  private readonly webhookUrl = '/api/v1/merchant/webhooks';

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
    return this.http.get<WebhookSettings>(`${this.webhookUrl}/settings`);
  }

  updateWebhookSettings(
    url: string,
    active: boolean,
    subscribedEvents: string[],
    regenerateSecret: boolean
  ): Observable<WebhookSettings> {
    return this.http.put<WebhookSettings>(`${this.webhookUrl}/settings`, {
      url,
      active,
      subscribedEvents,
      regenerateSecret,
    });
  }

  triggerTestWebhook(targetUrlOverride?: string): Observable<TestWebhookResult> {
    const override = targetUrlOverride?.trim();
    return this.http.post<TestWebhookResult>(`${this.webhookUrl}/test`,
      override ? { targetUrlOverride: override } : {});
  }

  getDeliveryLogs(statusFilter?: string, page = 0, size = 20): Observable<WebhookDeliveryPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (statusFilter && statusFilter !== 'ALL') {
      params = params.set('status', statusFilter);
    }
    return this.http.get<WebhookDeliveryPage>(`${this.webhookUrl}/deliveries`, { params });
  }

  getDeliveryDetail(deliveryId: string): Observable<WebhookDeliveryDetail> {
    return this.http.get<WebhookDeliveryDetail>(`${this.webhookUrl}/deliveries/${encodeURIComponent(deliveryId)}`);
  }

  retryDelivery(deliveryId: string): Observable<WebhookDeliveryDetail> {
    return this.http.post<WebhookDeliveryDetail>(
      `${this.webhookUrl}/deliveries/${encodeURIComponent(deliveryId)}/retry`, {});
  }
}
