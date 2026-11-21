import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { AuthService } from 'shared-auth';
import { NotificationBellComponent } from './notification/notification-bell/notification-bell.component';
import { AdminSearchService } from './pages/admin/admin-search.service';
import { MerchantApprovalService } from './pages/admin/merchant-approval.service';
import { AdminAuditService, AuditRecord } from './pages/admin/admin-audit.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet, RouterLink, NotificationBellComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  public authService = inject(AuthService);
  private readonly router = inject(Router);
  public readonly merchantApprovalService = inject(MerchantApprovalService);
  public readonly adminSearchService = inject(AdminSearchService);
  public readonly adminAuditService = inject(AdminAuditService);
  protected readonly title = signal('web');

  private static readonly DISMISSED_ALERTS_KEY = 'swiftdrop_dismissed_security_alerts';

  readonly pendingMerchantCount = computed(() => this.merchantApprovalService.pendingCount());
  readonly securityAlerts = signal<AuditRecord[]>([]);
  readonly dismissedAlertIds = signal<Set<string>>(this.loadDismissedAlertIds());
  readonly activeSecurityAlerts = computed(() =>
    this.securityAlerts().filter((a) => !this.dismissedAlertIds().has(a.id))
  );
  readonly totalAdminNotificationCount = computed(
    () => this.pendingMerchantCount() + this.activeSecurityAlerts().length
  );
  readonly isNotificationOpen = signal(false);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects || e.url)
    ),
    { initialValue: this.router.url }
  );

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => {
        this.closeAdminNotifications();
        if (!this.isAdminPage()) {
          this.adminSearchService.clear();
        }
        if (this.isAdminPage() && this.authService.hasRole('ADMIN')) {
          this.refreshAdminNotifications();
        }
      });
  }

  toggleAdminNotifications(): void {
    this.isNotificationOpen.update((open) => !open);
    if (this.isNotificationOpen()) {
      this.refreshAdminNotifications();
    }
  }

  closeAdminNotifications(): void {
    this.isNotificationOpen.set(false);
  }

  refreshAdminNotifications(): void {
    this.refreshMerchantPendingCount();
    this.loadSecurityAlerts();
  }

  refreshMerchantPendingCount(): void {
    if (this.authService.hasRole('ADMIN')) {
      this.merchantApprovalService.loadPendingCount().subscribe({
        error: () => undefined,
      });
    }
  }

  loadSecurityAlerts(): void {
    if (this.authService.hasRole('ADMIN')) {
      this.adminAuditService.loadSecurityAlerts(10).subscribe({
        next: (res) => {
          this.securityAlerts.set(res.content || []);
        },
        error: () => undefined,
      });
    }
  }

  private loadDismissedAlertIds(): Set<string> {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const stored = localStorage.getItem(App.DISMISSED_ALERTS_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          if (Array.isArray(parsed)) {
            return new Set<string>(parsed);
          }
        }
      }
    } catch {
      // Ignore localStorage errors (e.g. storage disabled / SSR / private mode)
    }
    return new Set<string>();
  }

  private saveDismissedAlertIds(ids: Set<string>): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const arr = Array.from(ids).slice(-200);
        localStorage.setItem(App.DISMISSED_ALERTS_KEY, JSON.stringify(arr));
      }
    } catch {
      // Ignore localStorage errors
    }
  }

  dismissSecurityAlert(alertId: string, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.dismissedAlertIds.update((set) => {
      const next = new Set([...set, alertId]);
      this.saveDismissedAlertIds(next);
      return next;
    });
  }

  isAuthPage(): boolean {
    const url = this.currentUrl() || this.router.url || '';
    const cleanUrl = url.split('?')[0].split('#')[0];
    return (
      cleanUrl === '' ||
      cleanUrl === '/' ||
      cleanUrl === '/login' ||
      cleanUrl === '/register' ||
      cleanUrl === '/merchant-registration' ||
      cleanUrl === '/forgot-password' ||
      cleanUrl === '/reset-password' ||
      cleanUrl === '/auth/callback' ||
      cleanUrl.startsWith('/auth/') ||
      cleanUrl === '/customer' ||
      cleanUrl.startsWith('/customer/') ||
      cleanUrl === '/courier' ||
      cleanUrl.startsWith('/courier/') ||
      cleanUrl === '/track' ||
      cleanUrl.startsWith('/track/') ||
      cleanUrl === '/dispatch' ||
      cleanUrl.startsWith('/dispatch/')
    );
  }

  isAdminPage(): boolean {
    const url = this.currentUrl() || this.router.url || '';
    const cleanUrl = url.split('?')[0].split('#')[0];
    return cleanUrl === '/admin' || cleanUrl.startsWith('/admin/');
  }

  isAdminSection(path: string): boolean {
    const url = this.currentUrl() || this.router.url || '';
    const cleanUrl = url.split('?')[0].split('#')[0];
    return path === '/admin' ? cleanUrl === path : cleanUrl.startsWith(path);
  }

  isMerchantPage(): boolean {
    const url = this.currentUrl() || this.router.url || '';
    const cleanUrl = url.split('?')[0].split('#')[0];
    return cleanUrl === '/merchant' || cleanUrl.startsWith('/merchant/');
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/']),
      error: () => this.router.navigate(['/']),
    });
  }
}
