import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification.service';
import { NotificationDto, NotificationPageResponse } from './notification.model';

describe('NotificationService', () => {
  let service: NotificationService;
  let http: HttpTestingController;

  const mockItem1: NotificationDto = {
    id: 'notif-1',
    title: 'Parcel In Transit',
    message: 'Your parcel is in transit.',
    channel: 'IN_APP',
    parcelId: 'pcl-1',
    trackingNumber: 'SD-20261104-001',
    read: false,
    createdAt: '2026-11-04T10:00:00Z',
  };

  const mockItem2: NotificationDto = {
    id: 'notif-2',
    title: 'Parcel Delivered',
    message: 'Your parcel has been delivered.',
    channel: 'IN_APP',
    parcelId: 'pcl-2',
    trackingNumber: 'SD-20261104-002',
    read: true,
    createdAt: '2026-11-04T09:00:00Z',
  };

  const mockPage: NotificationPageResponse = {
    items: [mockItem1, mockItem2],
    totalElements: 2,
    unreadCount: 1,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), NotificationService],
    });
    service = TestBed.inject(NotificationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts with empty signals', () => {
    expect(service.notifications()).toEqual([]);
    expect(service.unreadCount()).toBe(0);
    expect(service.totalElements()).toBe(0);
    expect(service.isLoading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('requests first page with default query parameters', () => {
    service.loadNotifications().subscribe((res) => {
      expect(res).toEqual(mockPage);
    });

    const req = http.expectOne((r) => r.url === '/api/v1/notifications');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('unreadOnly')).toBe('false');

    req.flush(mockPage);

    expect(service.notifications()).toEqual([mockItem1, mockItem2]);
    expect(service.unreadCount()).toBe(1);
    expect(service.totalElements()).toBe(2);
    expect(service.isLoading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('sends custom pagination parameters', () => {
    service.loadNotifications(2, 10, true).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/notifications');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('unreadOnly')).toBe('true');

    req.flush({ items: [], totalElements: 0, unreadCount: 0 });
  });

  it('appends notifications when append=true without duplicating by id', () => {
    // Initial load
    service.loadNotifications(0, 1).subscribe();
    const req1 = http.expectOne((r) => r.url === '/api/v1/notifications');
    req1.flush({ items: [mockItem1], totalElements: 2, unreadCount: 1 });

    expect(service.notifications()).toEqual([mockItem1]);

    // Append second page (with one existing and one new)
    service.loadNotifications(1, 1, false, true).subscribe();
    const req2 = http.expectOne((r) => r.url === '/api/v1/notifications');
    req2.flush({ items: [mockItem1, mockItem2], totalElements: 2, unreadCount: 1 });

    expect(service.notifications()).toEqual([mockItem1, mockItem2]);
  });

  it('sets error state when loadNotifications fails', () => {
    service.loadNotifications().subscribe({
      error: () => {
        expect(service.error()).toContain('Failed to load notifications');
        expect(service.isLoading()).toBe(false);
      },
    });

    const req = http.expectOne((r) => r.url === '/api/v1/notifications');
    req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
  });

  it('marks a single notification as read and decrements unreadCount', () => {
    // Seed with mockPage
    service.loadNotifications().subscribe();
    http.expectOne((r) => r.url === '/api/v1/notifications').flush(mockPage);

    expect(service.unreadCount()).toBe(1);

    service.markAsRead('notif-1').subscribe((dto) => {
      expect(dto.read).toBe(true);
    });

    const req = http.expectOne('/api/v1/notifications/notif-1/read');
    expect(req.request.method).toBe('POST');
    req.flush({ ...mockItem1, read: true });

    expect(service.unreadCount()).toBe(0);
    const updated = service.notifications().find((n) => n.id === 'notif-1');
    expect(updated?.read).toBe(true);
  });

  it('does not falsely update read state when markAsRead fails', () => {
    service.loadNotifications().subscribe();
    http.expectOne((r) => r.url === '/api/v1/notifications').flush(mockPage);

    expect(service.unreadCount()).toBe(1);

    service.markAsRead('notif-1').subscribe({
      error: () => {
        // Notification should still be unread
        const item = service.notifications().find((n) => n.id === 'notif-1');
        expect(item?.read).toBe(false);
        expect(service.unreadCount()).toBe(1);
      },
    });

    const req = http.expectOne('/api/v1/notifications/notif-1/read');
    req.flush('Not Found', { status: 404, statusText: 'Not Found' });
  });

  it('marks all unread notifications as read and refreshes unread count', () => {
    service.loadNotifications().subscribe();
    http.expectOne((r) => r.url === '/api/v1/notifications').flush(mockPage);

    expect(service.unreadCount()).toBe(1);

    service.markAllAsRead().subscribe();

    // Expect POST for notif-1 (the only unread one)
    const postReq = http.expectOne('/api/v1/notifications/notif-1/read');
    postReq.flush({ ...mockItem1, read: true });

    // Expect refresh count GET
    const refreshReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1');
    refreshReq.flush({ items: [], totalElements: 2, unreadCount: 0 });

    expect(service.unreadCount()).toBe(0);
    expect(service.notifications().every((n) => n.read)).toBe(true);
  });

  it('handles partial failure in markAllAsRead without marking failed items', () => {
    const unreadItemB: NotificationDto = {
      ...mockItem2,
      id: 'notif-3',
      read: false,
    };
    service.loadNotifications().subscribe();
    http.expectOne((r) => r.url === '/api/v1/notifications').flush({
      items: [mockItem1, unreadItemB],
      totalElements: 2,
      unreadCount: 2,
    });

    service.markAllAsRead().subscribe();

    // First item fails
    const postReq1 = http.expectOne('/api/v1/notifications/notif-1/read');
    postReq1.flush('Error', { status: 500, statusText: 'Error' });

    // Second item succeeds
    const postReq2 = http.expectOne('/api/v1/notifications/notif-3/read');
    postReq2.flush({ ...unreadItemB, read: true });

    // Reconcile count from server
    const refreshReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1');
    refreshReq.flush({ items: [], totalElements: 2, unreadCount: 1 });

    expect(service.unreadCount()).toBe(1);
    expect(service.notifications().find((n) => n.id === 'notif-1')?.read).toBe(false);
    expect(service.notifications().find((n) => n.id === 'notif-3')?.read).toBe(true);
  });

  it('clears all state via clear()', () => {
    service.loadNotifications().subscribe();
    http.expectOne((r) => r.url === '/api/v1/notifications').flush(mockPage);

    expect(service.notifications().length).toBe(2);

    service.clear();

    expect(service.notifications()).toEqual([]);
    expect(service.unreadCount()).toBe(0);
    expect(service.totalElements()).toBe(0);
    expect(service.isLoading()).toBe(false);
    expect(service.error()).toBeNull();
  });
});
