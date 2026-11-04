import { Injectable, inject, signal, effect } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, from } from 'rxjs';
import { catchError, concatMap, map, switchMap, tap, toArray } from 'rxjs/operators';
import { AuthService } from 'shared-auth';
import { NotificationDto, NotificationItem, NotificationPageResponse } from './notification.model';

/**
 * NotificationService
 *
 * Connects the Angular notification bell to the real authenticated
 * integration-service notification API (GET /api/v1/notifications
 * and POST /api/v1/notifications/{id}/read).
 */
@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService, { optional: true });
  static readonly URL = '/api/v1/notifications';
  private readonly baseUrl = NotificationService.URL;

  private readonly _notifications = signal<NotificationItem[]>([]);
  private readonly _unreadCount = signal<number>(0);
  private readonly _totalElements = signal<number>(0);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  readonly notifications = this._notifications.asReadonly();
  readonly unreadCount = this._unreadCount.asReadonly();
  readonly totalElements = this._totalElements.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly error = this._error.asReadonly();

  private lastUserId: string | null = null;

  constructor() {
    if (this.authService) {
      effect(() => {
        const user = this.authService?.currentUser();
        const currentUserId = user?.id ?? null;
        if (!user || !this.authService?.isAuthenticated()) {
          this.clear();
          this.lastUserId = null;
        } else if (currentUserId !== this.lastUserId) {
          this.clear();
          this.lastUserId = currentUserId;
          this.refreshUnreadCount().subscribe({ error: () => {} });
        }
      });
    }
  }

  /**
   * Refreshes the unread count from the server.
   */
  refreshUnreadCount(): Observable<number> {
    const params = new HttpParams().set('page', '0').set('size', '1');
    return this.http.get<NotificationPageResponse>(this.baseUrl, { params }).pipe(
      map((res) => {
        this._unreadCount.set(res.unreadCount);
        this._totalElements.set(res.totalElements);
        return res.unreadCount;
      }),
      catchError(() => of(this._unreadCount()))
    );
  }

  /**
   * Loads notifications from the real API.
   * If append is true, appends new items to existing list.
   */
  loadNotifications(
    page = 0,
    size = 20,
    unreadOnly = false,
    append = false
  ): Observable<NotificationPageResponse> {
    this._isLoading.set(true);
    this._error.set(null);

    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('unreadOnly', unreadOnly.toString());

    return this.http.get<NotificationPageResponse>(this.baseUrl, { params }).pipe(
      tap({
        next: (response) => {
          if (append) {
            const existingIds = new Set(this._notifications().map((n) => n.id));
            const newItems = response.items.filter((item) => !existingIds.has(item.id));
            this._notifications.update((current) => [...current, ...newItems]);
          } else {
            this._notifications.set(response.items);
          }
          this._totalElements.set(response.totalElements);
          this._unreadCount.set(response.unreadCount);
          this._isLoading.set(false);
          this._error.set(null);
        },
        error: () => {
          this._isLoading.set(false);
          this._error.set('Failed to load notifications. Please try again.');
        },
      })
    );
  }

  /**
   * Alias for loadNotifications for compatibility.
   */
  listNotifications(
    page = 0,
    size = 20,
    unreadOnly = false
  ): Observable<NotificationPageResponse> {
    return this.loadNotifications(page, size, unreadOnly, false);
  }

  /**
   * Marks a single notification as read on the backend.
   */
  markAsRead(id: string): Observable<NotificationDto> {
    return this.http.post<NotificationDto>(`${this.baseUrl}/${id}/read`, {}).pipe(
      tap({
        next: () => {
          let wasUnread = false;
          this._notifications.update((list) =>
            list.map((n) => {
              if (n.id === id) {
                if (!n.read) wasUnread = true;
                return { ...n, read: true };
              }
              return n;
            })
          );
          if (wasUnread) {
            this._unreadCount.update((count) => Math.max(0, count - 1));
          }
        },
      })
    );
  }

  /**
   * Marks all unread loaded notifications as read sequentially.
   * Confirms each with the backend and refreshes the unread count.
   */
  markAllAsRead(): Observable<void> {
    const unread = this._notifications().filter((n) => !n.read);
    if (unread.length === 0) {
      return of(void 0);
    }

    return from(unread).pipe(
      concatMap((item) =>
        this.markAsRead(item.id).pipe(
          catchError(() => of(null))
        )
      ),
      toArray(),
      switchMap(() => this.refreshUnreadCount()),
      map(() => void 0)
    );
  }

  /**
   * Clears state on logout or account switch.
   */
  clear(): void {
    this._notifications.set([]);
    this._unreadCount.set(0);
    this._totalElements.set(0);
    this._isLoading.set(false);
    this._error.set(null);
  }
}
