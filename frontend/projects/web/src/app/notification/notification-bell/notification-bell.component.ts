import {
  Component,
  computed,
  inject,
  signal,
  HostListener,
  ElementRef,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { NotificationService } from '../notification.service';
import { NotificationItem } from '../notification.model';

/**
 * NotificationBellComponent
 *
 * Displays a bell icon with an unread-count badge. Clicking the bell
 * opens a dropdown listing the most recent notifications. Individual
 * notifications can be marked as read; there is also a "Mark all read"
 * action.
 *
 * Data source: NotificationService (mock signals for Week 2).
 * Swap-in: when the backend is reachable, NotificationService methods
 * will call the integration-service API transparently.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule, DatePipe],
  template: `
    <div class="relative" #bellWrapper>
      <!-- Bell button -->
      <button
        type="button"
        id="notification-bell-btn"
        (click)="toggleDropdown()"
        class="relative flex items-center justify-center w-9 h-9 rounded-xl
               text-slate-500 hover:text-indigo-600 hover:bg-indigo-50
               transition-colors focus:outline-none focus-visible:ring-2
               focus-visible:ring-indigo-500"
        [attr.aria-label]="ariaLabel()"
        aria-haspopup="true"
        [attr.aria-expanded]="isOpen()"
      >
        <!-- Bell SVG -->
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="w-5 h-5"
          aria-hidden="true"
        >
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>

        <!-- Unread badge -->
        @if (unreadCount() > 0) {
          <span
            id="notification-unread-badge"
            class="absolute -top-1 -right-1 flex items-center justify-center
                   min-w-[18px] h-[18px] px-1 rounded-full
                   bg-red-500 text-white text-[10px] font-bold leading-none
                   ring-2 ring-white"
            aria-hidden="true"
          >{{ unreadCount() > 99 ? '99+' : unreadCount() }}</span>
        }
      </button>

      <!-- Dropdown panel -->
      @if (isOpen()) {
        <div
          id="notification-dropdown"
          role="dialog"
          aria-label="Notifications"
          class="absolute right-0 mt-2 w-80 sm:w-96 bg-white rounded-2xl shadow-xl
                 border border-slate-200 z-50 flex flex-col overflow-hidden
                 animate-fade-in"
        >
          <!-- Header -->
          <div class="flex items-center justify-between px-4 py-3 border-b border-slate-100">
            <h2 class="text-sm font-semibold text-slate-800">
              Notifications
              @if (unreadCount() > 0) {
                <span
                  class="ml-2 inline-flex items-center px-1.5 py-0.5 rounded-full
                         bg-indigo-100 text-indigo-700 text-[11px] font-bold"
                >{{ unreadCount() }} unread</span>
              }
            </h2>
            @if (unreadCount() > 0) {
              <button
                type="button"
                id="notification-mark-all-btn"
                (click)="onMarkAllRead()"
                class="text-xs font-medium text-indigo-600 hover:text-indigo-800
                       hover:underline focus:outline-none"
              >
                Mark all read
              </button>
            }
          </div>

          <!-- Notification list -->
          <ul
            class="overflow-y-auto max-h-[360px] divide-y divide-slate-100"
            role="list"
          >
            @for (item of visibleItems(); track item.id) {
              <li
                [id]="'notification-item-' + item.id"
                [class.bg-indigo-50]="!item.read"
                class="flex items-start gap-3 px-4 py-3 hover:bg-slate-50 transition-colors cursor-pointer"
                (click)="onMarkRead(item)"
                role="listitem"
                [attr.aria-label]="item.title + (item.read ? '' : ' (unread)')"
              >
                <!-- Status dot -->
                <span
                  class="mt-1.5 flex-shrink-0 w-2 h-2 rounded-full"
                  [class.bg-indigo-500]="!item.read"
                  [class.bg-transparent]="item.read"
                  aria-hidden="true"
                ></span>

                <!-- Content -->
                <div class="flex-1 min-w-0">
                  <p
                    class="text-sm font-medium text-slate-800 truncate"
                    [class.font-semibold]="!item.read"
                  >{{ item.title }}</p>
                  <p class="text-xs text-slate-500 mt-0.5 line-clamp-2">{{ item.message }}</p>
                  <p class="text-[11px] text-slate-400 mt-1">
                    {{ item.createdAt | date:'d MMM y, h:mm a' }}
                  </p>
                </div>
              </li>
            } @empty {
              <li class="flex flex-col items-center justify-center py-12 text-center px-4">
                <span class="text-3xl mb-2" aria-hidden="true">🔔</span>
                <p class="text-sm font-medium text-slate-500">You're all caught up!</p>
                <p class="text-xs text-slate-400 mt-1">No notifications yet.</p>
              </li>
            }
          </ul>

          <!-- Footer -->
          @if (hasMore()) {
            <div class="border-t border-slate-100 px-4 py-2 text-center">
              <button
                type="button"
                id="notification-load-more-btn"
                (click)="loadMore()"
                class="text-xs font-medium text-indigo-600 hover:underline focus:outline-none"
              >
                Load more
              </button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes fade-in {
      from { opacity: 0; transform: translateY(-4px); }
      to   { opacity: 1; transform: translateY(0);    }
    }
    .animate-fade-in {
      animation: fade-in 120ms ease-out;
    }
  `],
})
export class NotificationBellComponent {
  private readonly notificationService = inject(NotificationService);
  private readonly el = inject(ElementRef);

  protected readonly isOpen = signal(false);
  protected readonly pageSize = 5;
  protected readonly visibleCount = signal(this.pageSize);

  readonly unreadCount = this.notificationService.unreadCount;

  protected readonly ariaLabel = computed(() => {
    const count = this.unreadCount();
    return count > 0
      ? `Notifications — ${count} unread`
      : 'Notifications — no unread messages';
  });

  protected readonly allItems = this.notificationService.notifications;

  protected readonly visibleItems = computed(() =>
    this.allItems().slice(0, this.visibleCount())
  );

  protected readonly hasMore = computed(
    () => this.allItems().length > this.visibleCount()
  );

  toggleDropdown(): void {
    this.isOpen.update((v) => !v);
    if (this.isOpen()) {
      this.visibleCount.set(this.pageSize); // reset paging on open
    }
  }

  onMarkRead(item: NotificationItem): void {
    if (!item.read) {
      this.notificationService.markAsRead(item.id).subscribe();
    }
  }

  onMarkAllRead(): void {
    this.notificationService.markAllAsRead().subscribe();
  }

  loadMore(): void {
    this.visibleCount.update((c) => c + this.pageSize);
  }

  /** Close dropdown when clicking outside the bell wrapper */
  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: EventTarget | null): void {
    if (target && !this.el.nativeElement.contains(target)) {
      this.isOpen.set(false);
    }
  }

  /** Close on Escape */
  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.isOpen.set(false);
  }
}

