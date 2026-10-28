import { Injectable, signal, computed } from '@angular/core';
import { Observable, of, tap } from 'rxjs';
import { NotificationItem, NotificationPage } from './notification.model';

/**
 * NotificationService
 *
 * Week 2 implementation: uses in-memory mock data (aligned with mock layer
 * convention used by courier, customer, and merchant services). When the
 * integration-service API is accessible at runtime, replace the `of(...)`
 * calls with real HttpClient calls to:
 *   GET  /api/v1/notifications?page=0&size=20&unreadOnly=false
 *   POST /api/v1/notifications/{id}/read
 *
 * Swap-in note: inject HttpClient and remove mock signal internals.
 */
@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private readonly _notifications = signal<NotificationItem[]>([
    {
      id: 'notif-mock-001',
      userId: 'user-mock-001',
      eventId: 'evt-mock-001',
      channel: 'IN_APP',
      title: 'Parcel In Transit',
      message:
        'Your parcel SD-20261028-H9K3N1 is now in transit and on its way to the destination.',
      read: false,
      createdAt: new Date(Date.now() - 1800000).toISOString(),
    },
    {
      id: 'notif-mock-002',
      userId: 'user-mock-001',
      eventId: 'evt-mock-002',
      channel: 'IN_APP',
      title: 'Parcel Delivered',
      message:
        'Great news! Your parcel SD-20261026-F4P8W2 has been successfully delivered.',
      read: false,
      createdAt: new Date(Date.now() - 7200000).toISOString(),
    },
    {
      id: 'notif-mock-003',
      userId: 'user-mock-001',
      eventId: 'evt-mock-003',
      channel: 'IN_APP',
      title: 'Courier Assigned',
      message:
        'A courier has been assigned to your parcel SD-20261027-B7R1Q9 and will pick it up shortly.',
      read: true,
      createdAt: new Date(Date.now() - 86400000).toISOString(),
    },
    {
      id: 'notif-mock-004',
      userId: 'user-mock-001',
      eventId: 'evt-mock-004',
      channel: 'IN_APP',
      title: 'Delivery Failed',
      message:
        'Unfortunately, delivery of SD-20261025-K2L9P3 failed. Reason: Customer premises closed.',
      read: true,
      createdAt: new Date(Date.now() - 172800000).toISOString(),
    },
    {
      id: 'notif-mock-005',
      userId: 'user-mock-001',
      eventId: 'evt-mock-005',
      channel: 'IN_APP',
      title: 'Parcel Created',
      message:
        'Your parcel booking SD-20261024-T4N8Q1 is confirmed. Pickup scheduled.',
      read: true,
      createdAt: new Date(Date.now() - 259200000).toISOString(),
    },
  ]);

  /** Read-only signal — components observe this for reactive updates */
  readonly notifications = this._notifications.asReadonly();

  /** Unread count derived signal */
  readonly unreadCount = computed(
    () => this._notifications().filter((n) => !n.read).length
  );

  /**
   * Fetch a page of notifications.
   * Mock implementation returns all items; real impl calls the API.
   */
  listNotifications(
    page = 0,
    size = 20,
    unreadOnly = false
  ): Observable<NotificationPage> {
    let items = this._notifications();
    if (unreadOnly) {
      items = items.filter((n) => !n.read);
    }
    const start = page * size;
    const content = items.slice(start, start + size);
    const result: NotificationPage = {
      content,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / size),
      page,
      size,
    };
    return of(result);
  }

  /**
   * Mark a single notification as read.
   * Mock implementation updates the in-memory signal.
   * Real impl: POST /api/v1/notifications/{id}/read then update local state.
   */
  markAsRead(id: string): Observable<void> {
    this._notifications.update((list) =>
      list.map((n) => (n.id === id ? { ...n, read: true } : n))
    );
    return of(void 0);
  }

  /**
   * Mark all notifications as read.
   */
  markAllAsRead(): Observable<void> {
    this._notifications.update((list) => list.map((n) => ({ ...n, read: true })));
    return of(void 0);
  }
}

