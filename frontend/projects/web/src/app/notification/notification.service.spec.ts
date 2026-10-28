import { NotificationService } from './notification.service';
import { firstValueFrom } from 'rxjs';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    service = new NotificationService();
  });

  it('should be created with pre-seeded mock notifications', () => {
    expect(service).toBeTruthy();
    expect(service.notifications().length).toBeGreaterThan(0);
  });

  it('should report correct unread count on initialisation', () => {
    const allItems = service.notifications();
    const expectedUnread = allItems.filter((n) => !n.read).length;
    expect(service.unreadCount()).toBe(expectedUnread);
  });

  it('should return a page of notifications via listNotifications', async () => {
    const page = await firstValueFrom(service.listNotifications(0, 20, false));
    expect(page.content.length).toBe(service.notifications().length);
    expect(page.page).toBe(0);
    expect(page.size).toBe(20);
    expect(page.totalElements).toBe(service.notifications().length);
  });

  it('should filter to unread-only when unreadOnly=true', async () => {
    const page = await firstValueFrom(service.listNotifications(0, 20, true));
    expect(page.content.every((n) => !n.read)).toBe(true);
    expect(page.content.length).toBe(service.unreadCount());
  });

  it('should decrement unreadCount after markAsRead', async () => {
    const unreadBefore = service.unreadCount();
    const unreadItem = service.notifications().find((n) => !n.read);
    expect(unreadItem).toBeDefined();

    await firstValueFrom(service.markAsRead(unreadItem!.id));

    expect(service.unreadCount()).toBe(unreadBefore - 1);
    expect(service.notifications().find((n) => n.id === unreadItem!.id)?.read).toBe(true);
  });

  it('should not change unreadCount when marking an already-read notification', async () => {
    const readItem = service.notifications().find((n) => n.read);
    expect(readItem).toBeDefined();
    const unreadBefore = service.unreadCount();

    await firstValueFrom(service.markAsRead(readItem!.id));

    expect(service.unreadCount()).toBe(unreadBefore);
  });

  it('should mark all notifications as read via markAllAsRead', async () => {
    await firstValueFrom(service.markAllAsRead());
    expect(service.unreadCount()).toBe(0);
    expect(service.notifications().every((n) => n.read)).toBe(true);
  });

  it('should paginate results correctly', async () => {
    const total = service.notifications().length;
    const pageSize = 2;
    const page0 = await firstValueFrom(service.listNotifications(0, pageSize, false));
    const page1 = await firstValueFrom(service.listNotifications(1, pageSize, false));

    expect(page0.content.length).toBe(pageSize);
    expect(page1.content.length).toBe(pageSize);
    expect(page0.totalElements).toBe(total);
    expect(Math.ceil(total / pageSize)).toBe(page0.totalPages);

    // No overlap between pages
    const ids0 = new Set(page0.content.map((n) => n.id));
    const ids1 = page1.content.map((n) => n.id);
    ids1.forEach((id) => expect(ids0.has(id)).toBe(false));
  });
});

