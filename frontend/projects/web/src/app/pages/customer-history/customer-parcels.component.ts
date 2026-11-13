import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  InputComponent,
  LoadingStateComponent,
  PaginationComponent,
  SelectComponent,
  SelectOption,
  ShellComponent,
  StatusBadgeComponent,
  TableCellDirective,
  TableColumn,
  TableComponent,
  humanizeStatus,
} from 'ui';
import { ReceiptButtonComponent } from '../tracking/receipt-button.component';
import { CustomerParcel, PARCEL_SEARCH_MAX, PARCEL_STATUSES, ParcelStatus } from './customer-history.model';
import { CustomerParcelsService } from './customer-parcels.service';
import { CUSTOMER_NAV, errorMessage } from './customer-nav';
import { PARCEL_COLUMNS, parcelKey } from './parcel-columns';

/** History table of all the customer's parcels (CU-05), newest first, with status, date and tracking-number filters and paging. */
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
    InputComponent,
    TableComponent,
    TableCellDirective,
    StatusBadgeComponent,
    PaginationComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    LoadingStateComponent,
    ReceiptButtonComponent,
  ],
  template: `
    <ui-shell [navItems]="nav" homeLink="/customer/dashboard" pageTitle="My parcels">
      <ui-card [padded]="false">
        <form class="grid grid-cols-1 gap-3 border-b border-surface-border p-4 sm:grid-cols-2 lg:grid-cols-4"
              role="search" aria-label="Filter your parcels" novalidate data-testid="history-filters"
              (submit)="$event.preventDefault(); applyFilters()">
          <ui-select
            inputId="status-filter"
            label="Status"
            placeholder="All statuses"
            [options]="statusOptions"
            [ngModel]="status()"
            [ngModelOptions]="{ standalone: true }"
            (ngModelChange)="filterByStatus($event)"
          />
          <ui-input inputId="from-filter" label="From" type="date" [(ngModel)]="fromInput" [ngModelOptions]="{ standalone: true }" />
          <ui-input inputId="to-filter" label="To" type="date" [(ngModel)]="toInput" [ngModelOptions]="{ standalone: true }" />
          <ui-input
            inputId="search-filter"
            label="Tracking number"
            type="search"
            placeholder="e.g. SD-20261112"
            autocomplete="off"
            [(ngModel)]="searchInput"
            [ngModelOptions]="{ standalone: true }"
          />
          <div class="flex flex-wrap items-center gap-3 sm:col-span-2 lg:col-span-4">
            <ui-button type="submit" size="sm" data-testid="apply-filters">Apply filters</ui-button>
            @if (hasFilters()) {
              <ui-button type="button" size="sm" variant="ghost" data-testid="clear-filters" (click)="clearFilters()">Clear filters</ui-button>
            }
            @if (filterError()) {
              <p class="text-sm text-danger-700" role="alert" data-testid="filter-error">{{ filterError() }}</p>
            }
            <p class="ml-auto text-sm text-ink-muted" aria-live="polite">
              @if (state() === 'ready') {
                {{ totalElements() }} {{ totalElements() === 1 ? 'parcel' : 'parcels' }}
              }
            </p>
          </div>
        </form>

        @if (state() === 'error') {
          <ui-error-state title="Could not load your parcels" [message]="error()" (retry)="load()" />
        } @else if (state() === 'ready' && rows().length === 0 && !hasFilters()) {
          <ui-empty-state title="No parcels yet" message="Send your first parcel and it will show up here.">
            <ui-button routerLink="/customer">New delivery</ui-button>
          </ui-empty-state>
        } @else {
          <!-- Phones: one card per parcel instead of a wide table -->
          <div class="sm:hidden" data-testid="parcel-cards">
            @if (state() === 'loading') {
              <ui-loading-state message="Loading your parcels…" />
            } @else if (rows().length === 0) {
              <ui-empty-state title="No parcels match these filters" message="Change the filters or clear them to see all parcels." />
            } @else {
              <ul class="divide-y divide-surface-border" aria-label="Your parcels, newest first">
                @for (row of rows(); track row.id) {
                  <li class="p-4" data-testid="parcel-card">
                    <a class="flex items-start justify-between gap-3" [routerLink]="['/customer/parcels', row.id]">
                      <span class="min-w-0">
                        <span class="block truncate font-mono font-medium text-brand-700">{{ row.trackingNumber }}</span>
                        <span class="mt-1 block truncate text-sm text-ink">To {{ row.recipientName }} · {{ row.dropoffAddress.city }}, {{ row.dropoffAddress.district }}</span>
                        <span class="mt-1 block text-xs text-ink-muted">{{ row.createdAt | date: 'mediumDate' }} · {{ row.fee | currency: 'LKR' : 'symbol' : '1.2-2' }}</span>
                      </span>
                      <ui-status-badge class="shrink-0" [status]="row.status" />
                    </a>
                    @if (row.status === 'DELIVERED') {
                      <app-receipt-button class="mt-3 block" [parcelId]="row.id" [trackingNumber]="row.trackingNumber" size="sm" />
                    }
                  </li>
                }
              </ul>
            }
          </div>

          <ui-table
            class="hidden sm:block"
            [columns]="columns"
            [rows]="rows()"
            [rowKey]="rowKey"
            [loading]="state() === 'loading'"
            loadingMessage="Loading your parcels…"
            emptyTitle="No parcels match these filters"
            emptyMessage="Change the filters or clear them to see all parcels."
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
            <ng-template uiCell="receipt" let-row>
              @if ($any(row).status === 'DELIVERED') {
                <app-receipt-button [parcelId]="$any(row).id" [trackingNumber]="$any(row).trackingNumber" size="sm" variant="ghost" />
              }
            </ng-template>
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
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  protected readonly nav = CUSTOMER_NAV;
  /** History table columns plus a receipt download for delivered parcels. */
  protected readonly columns: TableColumn<CustomerParcel>[] = [...PARCEL_COLUMNS, { key: 'receipt', header: 'Receipt', value: () => '' }];
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
  /** Applied filters (what the list shows); the inputs below are only applied on "Apply filters". */
  readonly from = signal<string | null>(null);
  readonly to = signal<string | null>(null);
  readonly search = signal<string | null>(null);
  readonly filterError = signal('');
  readonly hasFilters = computed(() => !!(this.status() || this.from() || this.to() || this.search()));

  fromInput = '';
  toInput = '';
  searchInput = '';

  /**
   * Page and filters come from the URL (?page=2&status=DELIVERED&from=2026-11-01&to=2026-11-30&q=SD-2026),
   * so Back from a parcel keeps them. Anything unusable in the URL falls back to the default.
   */
  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const page = Number(params.get('page'));
    const status = params.get('status') as ParcelStatus | null;
    // The URL shows pages from 1; the API counts from 0.
    this.page.set(Number.isInteger(page) && page >= 1 ? page - 1 : 0);
    this.status.set(status && PARCEL_STATUSES.includes(status) ? status : null);
    let from = validDate(params.get('from'));
    let to = validDate(params.get('to'));
    if (from && to && from > to) {
      from = to = null;
    }
    const q = params.get('q')?.trim() ?? '';
    this.from.set(from);
    this.to.set(to);
    this.search.set(q && q.length <= PARCEL_SEARCH_MAX ? q : null);
    this.fromInput = this.from() ?? '';
    this.toInput = this.to() ?? '';
    this.searchInput = this.search() ?? '';
    this.load();
  }

  filterByStatus(status: ParcelStatus | null): void {
    this.status.set(status);
    this.page.set(0);
    this.syncUrl();
    this.load();
  }

  /** Checks the date range and search like M1's endpoint does, then shows page 1 of the result. */
  applyFilters(): void {
    const from = validDate(this.fromInput);
    const to = validDate(this.toInput);
    const q = this.searchInput.trim();
    if ((this.fromInput && !from) || (this.toInput && !to)) {
      this.filterError.set('Enter dates as YYYY-MM-DD.');
      return;
    }
    if (from && to && from > to) {
      this.filterError.set('"From" must be on or before "To".');
      return;
    }
    if (q.length > PARCEL_SEARCH_MAX) {
      this.filterError.set(`Search for at most ${PARCEL_SEARCH_MAX} characters of a tracking number.`);
      return;
    }
    this.filterError.set('');
    this.from.set(from);
    this.to.set(to);
    this.search.set(q || null);
    this.searchInput = q;
    this.page.set(0);
    this.syncUrl();
    this.load();
  }

  clearFilters(): void {
    this.fromInput = this.toInput = this.searchInput = '';
    this.filterError.set('');
    this.status.set(null);
    this.from.set(null);
    this.to.set(null);
    this.search.set(null);
    this.page.set(0);
    this.syncUrl();
    this.load();
  }

  goToPage(page: number): void {
    this.page.set(page);
    this.syncUrl();
    this.load();
  }

  private syncUrl(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: this.page() > 0 ? this.page() + 1 : null,
        status: this.status(),
        from: this.from(),
        to: this.to(),
        q: this.search(),
      },
      replaceUrl: true,
    });
  }

  load(): void {
    this.state.set('loading');
    this.request?.unsubscribe(); // a newer filter or page wins over a slower older request
    this.request = this.parcels
      .list({ page: this.page(), size: this.size, status: this.status(), from: this.from(), to: this.to(), q: this.search() })
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

  /** Opens the parcel detail page with the live timeline. */
  track(parcel: CustomerParcel): void {
    void this.router.navigate(['/customer/parcels', parcel.id]);
  }
}

/** A real calendar date in YYYY-MM-DD form (as `<input type="date">` gives it), or null. */
function validDate(value: string | null | undefined): string | null {
  const text = value?.trim() ?? '';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    return null;
  }
  const date = new Date(`${text}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(text) ? text : null;
}
