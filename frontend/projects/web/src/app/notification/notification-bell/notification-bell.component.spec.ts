import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationService } from '../notification.service';

describe('NotificationBellComponent', () => {
  let fixture: ComponentFixture<NotificationBellComponent>;
  let component: NotificationBellComponent;
  let service: NotificationService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
    }).compileComponents();

    service = TestBed.inject(NotificationService);
    fixture = TestBed.createComponent(NotificationBellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should render the bell button', () => {
    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    expect(btn).toBeTruthy();
  });

  it('should show unread badge when there are unread notifications', () => {
    // Mock data has 2 unread items by default
    const badge = fixture.debugElement.query(By.css('#notification-unread-badge'));
    expect(badge).toBeTruthy();
    expect(badge.nativeElement.textContent.trim()).toBe('2');
  });

  it('should hide the badge when all notifications are read', fakeAsync(() => {
    service.markAllAsRead().subscribe();
    tick();
    fixture.detectChanges();

    const badge = fixture.debugElement.query(By.css('#notification-unread-badge'));
    expect(badge).toBeNull();
  }));

  it('should open the dropdown on bell click', () => {
    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    const dropdown = fixture.debugElement.query(By.css('#notification-dropdown'));
    expect(dropdown).toBeTruthy();
  });

  it('should close the dropdown on second bell click', () => {
    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();
    btn.nativeElement.click();
    fixture.detectChanges();

    const dropdown = fixture.debugElement.query(By.css('#notification-dropdown'));
    expect(dropdown).toBeNull();
  });

  it('should list notification items inside the open dropdown', () => {
    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    const items = fixture.debugElement.queryAll(By.css('[id^="notification-item-"]'));
    // Component shows up to pageSize (5) items initially
    expect(items.length).toBeGreaterThan(0);
  });

  it('should decrement badge count when an unread notification is clicked', fakeAsync(() => {
    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    const unreadBefore = service.unreadCount();
    // Click first list item that is unread
    const unreadItem = service.notifications().find((n) => !n.read)!;
    const itemEl = fixture.debugElement.query(
      By.css(`#notification-item-${unreadItem.id}`)
    );
    expect(itemEl).toBeTruthy();
    itemEl.nativeElement.click();
    tick();
    fixture.detectChanges();

    expect(service.unreadCount()).toBe(unreadBefore - 1);
    const badge = fixture.debugElement.query(By.css('#notification-unread-badge'));
    if (service.unreadCount() === 0) {
      expect(badge).toBeNull();
    } else {
      expect(badge.nativeElement.textContent.trim()).toBe(
        String(service.unreadCount())
      );
    }
  }));

  it('should mark all as read via "Mark all read" button', fakeAsync(() => {
    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    btn.nativeElement.click();
    fixture.detectChanges();

    const markAllBtn = fixture.debugElement.query(By.css('#notification-mark-all-btn'));
    expect(markAllBtn).toBeTruthy();
    markAllBtn.nativeElement.click();
    tick();
    fixture.detectChanges();

    expect(service.unreadCount()).toBe(0);
    const badge = fixture.debugElement.query(By.css('#notification-unread-badge'));
    expect(badge).toBeNull();
  }));

  it('should close the dropdown when Escape is pressed', () => {
    const bellBtn = fixture.debugElement.query(By.css('#notification-bell-btn'));
    bellBtn.nativeElement.click();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('#notification-dropdown'))).toBeTruthy();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('#notification-dropdown'))).toBeNull();
  });

  it('should set aria-expanded correctly on the bell button', () => {
    const btn = fixture.debugElement.query(By.css('#notification-bell-btn'));

    expect(btn.nativeElement.getAttribute('aria-expanded')).toBe('false');

    btn.nativeElement.click();
    fixture.detectChanges();
    expect(btn.nativeElement.getAttribute('aria-expanded')).toBe('true');
  });
});

