import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, inject, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from 'shared-auth';
import { AdminAuditService, AuditRecord } from './admin/admin-audit.service';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe],
  templateUrl: './admin.component.html',
})
export class AdminComponent implements OnInit {
  readonly authService = inject(AuthService, { optional: true });
  readonly auditService = inject(AdminAuditService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.loadActivities(0);
  }

  loadActivities(page: number): void {
    this.auditService.loadAuditLogs(page, 10).subscribe({
      error: () => {},
    });
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.auditService.totalPages()) {
      this.loadActivities(page);
    }
  }

  isAllowedAdministrativeActivity(record: AuditRecord): boolean {
    const action = (record.action || '').toUpperCase();
    const role = (record.actorRole || '').toUpperCase();
    const actor = (record.actorId || '').toLowerCase();
    const target = (record.targetId || '').toLowerCase();
    const email = (record.details?.['email'] || '').toLowerCase();

    // 1. Merchant registration / approval
    if (
      action.startsWith('MERCHANT_') ||
      action.includes('MERCHANT') ||
      (action === 'USER_REGISTERED' && (role === 'MERCHANT' || email.includes('merchant')))
    ) {
      return true;
    }

    // 2. Courier or Dispatcher creation / status
    if (
      action.startsWith('STAFF_') ||
      action.includes('COURIER') ||
      action.includes('DISPATCHER')
    ) {
      return true;
    }

    // 3. Admin password renew
    if (
      action.includes('PASSWORD') &&
      (role === 'ADMIN' || actor.includes('admin') || target.includes('admin') || email.includes('admin'))
    ) {
      return true;
    }

    return false;
  }

  readonly filteredActivities = computed(() => {
    return this.auditService.activities()
      .filter((r) => this.isAllowedAdministrativeActivity(r))
      .slice(0, 10);
  });

  formatAction(action: string, record?: AuditRecord): {
    title: string;
    area: string;
    areaClass: string;
    status: string;
    statusClass: string;
    link: string;
  } {
    const rawRole = (
      (record?.details && (record.details['role'] || record.details['targetRole'])) ||
      record?.actorRole ||
      ''
    ).toUpperCase();

    const isCourier = rawRole === 'COURIER' || action === 'COURIER_CREATED' || action.includes('COURIER');
    const isDispatcher = rawRole === 'DISPATCHER' || action === 'DISPATCHER_CREATED' || action.includes('DISPATCHER');

    const staffArea = isCourier ? 'COURIER' : (isDispatcher ? 'DISPATCHER' : 'STAFF');
    const areaThemeClass = 'bg-[#faf5f1] text-[#9b432f] border-[#f2ded8]';

    switch (action) {
      case 'MERCHANT_APPROVED':
        return {
          title: 'Merchant Registration Approved',
          area: 'MERCHANT',
          areaClass: areaThemeClass,
          status: 'APPROVED',
          statusClass: 'bg-emerald-100 text-emerald-800',
          link: '/admin/merchants',
        };
      case 'MERCHANT_REJECTED':
        return {
          title: 'Merchant Registration Rejected',
          area: 'MERCHANT',
          areaClass: areaThemeClass,
          status: 'REJECTED',
          statusClass: 'bg-rose-100 text-rose-800',
          link: '/admin/merchants',
        };
      case 'MERCHANT_REGISTERED':
      case 'MERCHANT_PROVISIONED':
      case 'MERCHANT_ACTIVATED':
        return {
          title: 'Merchant Registration',
          area: 'MERCHANT',
          areaClass: areaThemeClass,
          status: 'REGISTERED',
          statusClass: 'bg-emerald-100 text-emerald-800',
          link: '/admin/merchants',
        };
      case 'STAFF_PROVISIONED':
      case 'COURIER_CREATED':
      case 'DISPATCHER_CREATED':
        return {
          title: 'Staff Member Provisioned',
          area: staffArea,
          areaClass: areaThemeClass,
          status: 'PROVISIONED',
          statusClass: 'bg-purple-100 text-purple-800',
          link: '/admin/users',
        };
      case 'STAFF_ENABLED':
      case 'USER_ENABLED':
        return {
          title: 'Staff Account Enabled',
          area: staffArea,
          areaClass: areaThemeClass,
          status: 'ACTIVE',
          statusClass: 'bg-green-100 text-green-800',
          link: '/admin/users',
        };
      case 'STAFF_DISABLED':
      case 'USER_DISABLED':
        return {
          title: 'Staff Account Disabled',
          area: staffArea,
          areaClass: areaThemeClass,
          status: 'DISABLED',
          statusClass: 'bg-red-100 text-red-800',
          link: '/admin/users',
        };
      case 'ADMIN_PASSWORD_RESET':
      case 'PASSWORD_RESET_COMPLETED':
      case 'INITIAL_PASSWORD_CHANGED':
      case 'PASSWORD_RESET_SUCCESS':
        return {
          title: 'Admin Password Renewed',
          area: 'SECURITY',
          areaClass: areaThemeClass,
          status: 'RENEWED',
          statusClass: 'bg-amber-100 text-amber-800',
          link: '/admin',
        };
      default:
        return {
          title: action.replace(/_/g, ' '),
          area: 'GOVERNANCE',
          areaClass: areaThemeClass,
          status: 'COMPLETED',
          statusClass: 'bg-gray-100 text-gray-800',
          link: '/admin',
        };
    }
  }

  getDetailsText(record: AuditRecord): string {
    if (!record.details) {
      return record.targetId ? `Target: ${record.targetId}` : '';
    }
    const d = record.details;
    if (d['businessName'] && d['reason']) {
      return `${d['businessName']} — ${d['reason']}`;
    }
    if (d['email'] && d['fullName']) {
      return `${d['fullName']} (${d['email']})`;
    }
    if (d['businessName']) {
      return `${d['businessName']}`;
    }
    if (d['email']) {
      return `${d['email']}`;
    }
    if (d['fullName']) {
      return `${d['fullName']}`;
    }
    if (d['role']) {
      return `Role: ${d['role']}`;
    }
    return record.targetId ? `Target: ${record.targetId}` : '';
  }

  logout(): void {
    if (!this.authService) {
      void this.router.navigate(['/']);
      return;
    }

    this.authService.logout().subscribe({
      next: () => void this.router.navigate(['/']),
      error: () => void this.router.navigate(['/']),
    });
  }
}
