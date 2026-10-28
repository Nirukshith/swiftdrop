import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  PaginationComponent,
  SelectComponent,
  SelectOption,
  ShellComponent,
  StatusBadgeComponent,
  TableCellDirective,
  TableComponent,
  humanizeStatus,
} from 'ui';
import { CustomerParcel, PARCEL_STATUSES, ParcelStatus } from './customer-history.model';
import { CustomerParcelsService } from './customer-parcels.service';
import { CUSTOMER_NAV, errorMessage } from './customer-nav';
import { PARCEL_COLUMNS, parcelKey } from './parcel-columns';

/** History table of all the customer's parcels (CU-05), with status filter and paging. */
@Component({
  selector: 'app-customer-parcels',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    CurrencyPipe,
    ShellComponent,
    CardComponent,
    ButtonComponent,
    SelectComponent,
    TableComponent,
    TableCellDirective,
    StatusBadgeComponent,
    PaginationComponent,
    ErrorStateComponent,
    EmptyStateComponent,
  ],
  template: `
    <ui-shell [navItems]="nav" homeLink="/customer/dashboard" pageTitle="My parcels">
      <ui-card [padded]="false">
        <div class="flex flex-wrap items-end justify-between gap-3 border-b border-surface-border p-4">
          <ui-select
            class="w-full sm:w-64"
            inputId="status-filter"
            label="Status"
            placeholder="All statuses"
            [options]="statusOptions"
            [ngModel]="status()"
            (ngModelChange)="filterByStatus($event)"
          />
          <p class="text-sm text-ink-muted" aria-live="polite">
            @if (state() === 'ready') {
              {{ totalElements() }} {{ totalElements() === 1 ? 'parcel' : 'parcels' }}
            }
          </p>
        </div>

        @if (state() === 'error') {
          <ui-error-state title="Could not load your parcels" [message]="error()" (retry)="load()" />
        } @else if (state() === 'ready' && rows().length === 0 && !status()) {
          <ui-empty-state title="No parcels yet" message="Send your first parcel and it will show up here.">
            <ui-button routerLink="/customer">New delivery</ui-button>
          </ui-empty-state>
        } @else {
          <ui-table
            [columns]="columns"
            [rows]="rows()"
            [rowKey]="rowKey"
            [loading]="state() === 'loading'"
            loadingMessage="Loading your parcels…"
            emptyTitle="No parcels with this status"
            emptyMessage="Choose another status or show all."
            caption="Your parcels, newest first"
            [clickable]="true"
            (rowClick)="track($event)"
          >
            <ng-template uiCell="trackingNumber" let-row>
              <span class="font-mono font-medium text-brand-700">{{ $any(row).trackingNumber }}</span>
            </ng-template>
            <ng-template uiCell="status" let-row>
              <ui-status-badge [status]="$any(row).status" />
            </ng-template>
            <ng-template uiCell="createdAt" let-row>{{ $any(row).createdAt | date: 'mediumDate' }}</ng-template>
            <ng-template uiCell="fee" let-row>{{ $any(row).fee | currency: 'LKR' : 'symbol' : '1.2-2' }}</ng-template>
          </ui-table>

          @if (totalElements() > size) {
            <div class="border-t border-surface-border p-4">
              <ui-pagination
                [page]="page()"
                [size]="size"
                [totalElements]="totalElements()"
                (pageChange)="goToPage($event)"
              />
            </div>
          }
        }
      </ui-card>
    </ui-shell>
  `,
})
export class CustomerParcelsComponent implements OnInit {
  private readonly parcels = inject(CustomerParcelsService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  protected readonly nav = CUSTOMER_NAV;
  protected readonly columns = PARCEL_COLUMNS;
  protected readonly rowKey = parcelKey;
  protected readonly statusOptions: SelectOption<ParcelStatus>[] = PARCEL_STATUSES.map((s) => ({
    value: s,
    label: humanizeStatus(s),
  }));

  readonly size = 10;
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly error = signal('');
  readonly rows = signal<CustomerParcel[]>([]);
  readonly page = signal(0);
  readonly totalElements = signal(0);
  readonly status = signal<ParcelStatus | null>(null);

  ngOnInit(): void {
    this.load();
  }

  filterByStatus(status: ParcelStatus | null): void {
    this.status.set(status);
    this.page.set(0);
    this.load();
  }

  goToPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.request?.unsubscribe(); // a newer filter or page wins over a slower older request
    this.request = this.parcels
      .list({ page: this.page(), size: this.size, status: this.status() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.rows.set(result.content);
          this.totalElements.set(result.totalElements);
          this.state.set('ready');
        },
        error: (err: unknown) => {
          this.error.set(errorMessage(err));
          this.state.set('error');
        },
      });
  }

  /** The detail page with the live timeline comes in week 3; until then open the public tracking page. */
  track(parcel: CustomerParcel): void {
    void this.router.navigate(['/track', parcel.trackingNumber]);
  }
}
