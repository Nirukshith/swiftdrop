import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationService } from '../notification.service';
import { NotificationDto, NotificationPageResponse } from '../notification.model';

describe('NotificationBellComponent', () => {
  let fixture: ComponentFixture<NotificationBellComponent>;
  let component: NotificationBellComponent;
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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        NotificationService,
      ],
    }).compileComponents();

    service = TestBed.inject(NotificationService);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NotificationBellComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  it('creates the component and fetches initial unread count', () => {
    fixture.detectChanges();

    // ngOnInit calls refreshUnreadCount() (page=0, size=1)
    const req = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1');
    expect(req.request.method).toBe('GET');
    req.flush({ items: [], totalElements: 5, unreadCount: 3 });

    fixture.detectChanges();

    expect(component).toBeTruthy();
    const badge = fixture.debugElement.query(By.css('#notification-unread-badge'));
    expect(badge).toBeTruthy();
    expect(badge.nativeElement.textContent.trim()).toBe('3');
  });

  it('hides the unread badge when unreadCount is 0', () => {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1');
    req.flush({ items: [], totalElements: 0, unreadCount: 0 });

    fixture.detectChanges();

    const badge = fixture.debugElement.query(By.css('#notification-unread-badge'));
    expect(badge).toBeNull();
  });

  it('opens dropdown and requests notification page on bell click', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 2, unreadCount: 1 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    // Opening dropdown requests page 0 with size 5
    const pageReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0');
    expect(pageReq.request.params.get('size')).toBe('5');
    pageReq.flush(mockPage);

    fixture.detectChanges();

    const dropdown = fixture.debugElement.query(By.css('#notification-dropdown'));
    expect(dropdown).toBeTruthy();

    const items = fixture.debugElement.queryAll(By.css('[id^="notification-item-"]'));
    expect(items.length).toBe(2);
  });

  it('displays empty state when there are no notifications', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 0, unreadCount: 0 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    const pageReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0');
    pageReq.flush({ items: [], totalElements: 0, unreadCount: 0 });

    fixture.detectChanges();

    const empty = fixture.debugElement.query(By.css('#notification-empty'));
    expect(empty).toBeTruthy();
    expect(empty.nativeElement.textContent).toContain("You're all caught up!");
  });

  it('displays error state and retries on failure', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 0, unreadCount: 0 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    const pageReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0');
    pageReq.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });

    fixture.detectChanges();

    const errorEl = fixture.debugElement.query(By.css('#notification-error'));
    expect(errorEl).toBeTruthy();
    expect(errorEl.nativeElement.textContent).toContain('Failed to load notifications');

    // Click retry
    const retryBtn = fixture.debugElement.query(By.css('#notification-retry-btn'));
    expect(retryBtn).toBeTruthy();
    retryBtn.nativeElement.click();
    fixture.detectChanges();

    const retryReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0');
    retryReq.flush(mockPage);

    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('#notification-error'))).toBeNull();
    const items = fixture.debugElement.queryAll(By.css('[id^="notification-item-"]'));
    expect(items.length).toBe(2);
  });

  it('marks an unread notification as read on click and updates badge', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 2, unreadCount: 1 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0')
      .flush(mockPage);
    fixture.detectChanges();

    expect(service.unreadCount()).toBe(1);

    // Click unread item notif-1
    const itemEl = fixture.debugElement.query(By.css('#notification-item-notif-1'));
    expect(itemEl).toBeTruthy();
    itemEl.nativeElement.click();

    const markReq = http.expectOne('/api/v1/notifications/notif-1/read');
    expect(markReq.request.method).toBe('POST');
    markReq.flush({ ...mockItem1, read: true });

    fixture.detectChanges();

    expect(service.unreadCount()).toBe(0);
    const badge = fixture.debugElement.query(By.css('#notification-unread-badge'));
    expect(badge).toBeNull();
  });

  it('marks all as read when button is clicked', fakeAsync(() => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 2, unreadCount: 1 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0')
      .flush(mockPage);
    fixture.detectChanges();

    const markAllBtn = fixture.debugElement.query(By.css('#notification-mark-all-btn'));
    expect(markAllBtn).toBeTruthy();
    markAllBtn.nativeElement.click();

    // Mark notif-1
    const postReq = http.expectOne('/api/v1/notifications/notif-1/read');
    postReq.flush({ ...mockItem1, read: true });

    // Refresh count
    const refreshReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1');
    refreshReq.flush({ items: [], totalElements: 2, unreadCount: 0 });

    tick();
    fixture.detectChanges();

    expect(service.unreadCount()).toBe(0);
    expect(fixture.debugElement.query(By.css('#notification-unread-badge'))).toBeNull();
  }));

  it('shows Load More when totalElements exceeds loaded items and loads next page', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 6, unreadCount: 2 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    // Page 0 returns 2 items out of 6 total
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0')
      .flush({ items: [mockItem1, mockItem2], totalElements: 6, unreadCount: 2 });
    fixture.detectChanges();

    const loadMoreBtn = fixture.debugElement.query(By.css('#notification-load-more-btn'));
    expect(loadMoreBtn).toBeTruthy();

    loadMoreBtn.nativeElement.click();
    fixture.detectChanges();

    const nextReq = http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '1');
    expect(nextReq.request.params.get('size')).toBe('5');
    const mockItem3: NotificationDto = { ...mockItem1, id: 'notif-3' };
    nextReq.flush({ items: [mockItem3], totalElements: 6, unreadCount: 2 });
    fixture.detectChanges();

    const items = fixture.debugElement.queryAll(By.css('[id^="notification-item-"]'));
    expect(items.length).toBe(3);
  });

  it('closes dropdown on Escape key and outside click', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 0, unreadCount: 0 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0')
      .flush({ items: [], totalElements: 0, unreadCount: 0 });
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('#notification-dropdown'))).toBeTruthy();

    // Escape closes dropdown
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('#notification-dropdown'))).toBeNull();

    // Re-open
    btn.nativeElement.click();
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0')
      .flush({ items: [], totalElements: 0, unreadCount: 0 });
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('#notification-dropdown'))).toBeTruthy();

    // Outside click closes dropdown
    document.body.click();
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('#notification-dropdown'))).toBeNull();
  });

  it('sets aria-expanded correctly on the bell button', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('size') === '1')
      .flush({ items: [], totalElements: 0, unreadCount: 0 });

    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    expect(btn.nativeElement.getAttribute('aria-expanded')).toBe('false');

    btn.nativeElement.click();
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/notifications' && r.params.get('page') === '0')
      .flush({ items: [], totalElements: 0, unreadCount: 0 });
    fixture.detectChanges();

    expect(btn.nativeElement.getAttribute('aria-expanded')).toBe('true');
  });
});
