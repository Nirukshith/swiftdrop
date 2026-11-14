import {
  Component,
  OnInit,
  computed,
  inject,
  input,
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
 * Displays a bell icon with a real-time unread-count badge. Clicking the bell
 * opens an authenticated dropdown listing notifications from the backend API.
 * Supports marking individual items as read, marking all as read, server-side
 * pagination (load more), loading indicators, error retry, and keyboard/click-outside
 * dismissals.
 */
interface SidePanelPosition {
  left: number;
  top: number | null;
  bottom: number | null;
  maxHeight: number;
}

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
          class="w-80 sm:w-96 bg-white rounded-2xl shadow-xl
                 border border-slate-200 z-50 flex flex-col overflow-hidden
                 animate-fade-in"
          [class.absolute]="placement() === 'below'"
          [class.right-0]="placement() === 'below'"
          [class.mt-2]="placement() === 'below'"
          [class.fixed]="placement() === 'right'"
          [style.left.px]="sidePosition()?.left"
          [style.top.px]="sidePosition()?.top"
          [style.bottom.px]="sidePosition()?.bottom"
          [style.max-height.px]="sidePosition()?.maxHeight"
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

          <!-- Loading state on initial fetch -->
          @if (isLoading() && allItems().length === 0) {
            <div id="notification-loading" class="flex flex-col items-center justify-center py-10 text-center px-4" role="status">
              <span class="inline-block w-6 h-6 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin"></span>
              <p class="mt-2 text-xs text-slate-500">Loading notifications…</p>
            </div>
          } @else if (error() && allItems().length === 0) {
            <!-- Error state -->
            <div id="notification-error" class="flex flex-col items-center justify-center py-8 text-center px-4" role="alert">
              <span class="text-2xl mb-1 text-rose-500">⚠️</span>
              <p class="text-sm font-medium text-slate-700">Failed to load notifications</p>
              <p class="text-xs text-slate-500 mt-1">{{ error() }}</p>
              <button
                type="button"
                id="notification-retry-btn"
                (click)="retry()"
                class="mt-3 px-3 py-1.5 text-xs font-semibold text-indigo-600 bg-indigo-50 hover:bg-indigo-100 rounded-lg transition-colors focus:outline-none"
              >
                Retry
              </button>
            </div>
          } @else {
            <!-- Notification list -->
            <ul
              class="overflow-y-auto max-h-[360px] min-h-0 divide-y divide-slate-100"
              role="list"
            >
              @for (item of allItems(); track item.id) {
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
                <li id="notification-empty" class="flex flex-col items-center justify-center py-12 text-center px-4">
                  <span class="text-3xl mb-2" aria-hidden="true">🔔</span>
                  <p class="text-sm font-medium text-slate-500">You're all caught up!</p>
                  <p class="text-xs text-slate-400 mt-1">No notifications yet.</p>
                </li>
              }
            </ul>

            <!-- Footer / Load more -->
            @if (hasMore()) {
              <div class="border-t border-slate-100 px-4 py-2 text-center">
                <button
                  type="button"
                  id="notification-load-more-btn"
                  (click)="loadMore()"
                  [disabled]="isLoadingMore()"
                  class="text-xs font-medium text-indigo-600 hover:underline focus:outline-none disabled:opacity-50"
                >
                  {{ isLoadingMore() ? 'Loading…' : 'Load more' }}
                </button>
              </div>
            }
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
export class NotificationBellComponent implements OnInit {
  private readonly notificationService = inject(NotificationService);
  private readonly el = inject(ElementRef);

  /**
   * Where the dropdown opens. 'below' (default) drops down and aligns to the bell's right edge,
   * for top bars. 'right' opens beside the bell, for a narrow left sidebar where a panel aligned
   * to the right edge would be pushed off the screen.
   */
  readonly placement = input<'below' | 'right'>('below');

  protected readonly isOpen = signal(false);

  /** Screen position of the panel in 'right' placement; null in 'below' placement. */
  protected readonly sidePosition = signal<SidePanelPosition | null>(null);
  protected readonly pageSize = 5;
  protected readonly currentPage = signal(0);
  protected readonly isLoadingMore = signal(false);

  readonly unreadCount = this.notificationService.unreadCount;
  readonly allItems = this.notificationService.notifications;
  readonly totalElements = this.notificationService.totalElements;
  readonly isLoading = this.notificationService.isLoading;
  readonly error = this.notificationService.error;

  protected readonly ariaLabel = computed(() => {
    const count = this.unreadCount();
    return count > 0
      ? `Notifications — ${count} unread`
      : 'Notifications — no unread messages';
  });

  protected readonly hasMore = computed(
    () => this.allItems().length < this.totalElements()
  );

  ngOnInit(): void {
    this.notificationService.refreshUnreadCount().subscribe({ error: () => {} });
  }

  toggleDropdown(): void {
    const nextState = !this.isOpen();
    if (nextState) {
      this.positionSidePanel();
    }
    this.isOpen.set(nextState);
    if (nextState) {
      this.currentPage.set(0);
      this.notificationService.loadNotifications(0, this.pageSize, false, false).subscribe({ error: () => {} });
    }
  }

  retry(): void {
    this.notificationService.loadNotifications(this.currentPage(), this.pageSize, false, this.currentPage() > 0).subscribe({ error: () => {} });
  }

  loadMore(): void {
    if (this.isLoadingMore()) return;
    const nextPage = this.currentPage() + 1;
    this.isLoadingMore.set(true);
    this.notificationService.loadNotifications(nextPage, this.pageSize, false, true).subscribe({
      next: () => {
        this.currentPage.set(nextPage);
        this.isLoadingMore.set(false);
      },
      error: () => this.isLoadingMore.set(false),
    });
  }

  onMarkRead(item: NotificationItem): void {
    if (!item.read) {
      this.notificationService.markAsRead(item.id).subscribe({ error: () => {} });
    }
  }

  onMarkAllRead(): void {
    this.notificationService.markAllAsRead().subscribe({ error: () => {} });
  }

  /** Close dropdown when clicking outside the bell wrapper */
  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: EventTarget | null): void {
    if (target && !this.el.nativeElement.contains(target)) {
      this.isOpen.set(false);
    }
  }

  /** Close on Escape */
  /**
   * In 'right' placement the panel is fixed beside the bell, opening downward or upward (whichever
   * side of the bell has more room) and capped to the space on that side, so it always stays on screen
   * however tall the list is or wherever the bell sits in the sidebar.
   */
  @HostListener('window:resize')
  positionSidePanel(): void {
    if (this.placement() !== 'right') {
      this.sidePosition.set(null);
      return;
    }
    const bell = (this.el.nativeElement as HTMLElement).getBoundingClientRect();
    const margin = 16;
    const viewportHeight = window.innerHeight;
    const spaceBelow = viewportHeight - bell.top - margin;
    const spaceAbove = bell.bottom - margin;
    const left = bell.right + 24;
    this.sidePosition.set(
      spaceBelow >= spaceAbove
        ? { left, top: bell.top, bottom: null, maxHeight: spaceBelow }
        : { left, top: null, bottom: viewportHeight - bell.bottom, maxHeight: spaceAbove },
    );
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.isOpen.set(false);
  }
}
