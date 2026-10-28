import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
  ShellComponent,
  StatusBadgeComponent,
  TableCellDirective,
  TableComponent,
} from 'ui';
import { CustomerParcel } from './customer-history.model';
import { CustomerParcelsService } from './customer-parcels.service';
import { CUSTOMER_NAV, errorMessage } from './customer-nav';
import { PARCEL_COLUMNS, parcelKey } from './parcel-columns';

interface DashboardStats {
  total: number;
  outForDelivery: number;
  delivered: number;
}

/** Customer home (CU-05): counts and the most recent parcels. */
@Component({
  selector: 'app-customer-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    CurrencyPipe,
    ShellComponent,
    CardComponent,
    ButtonComponent,
    TableComponent,
    TableCellDirective,
    StatusBadgeComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    EmptyStateComponent,
  ],
  template: `
    <ui-shell [navItems]="nav" homeLink="/customer/dashboard" pageTitle="Dashboard">
      @switch (state()) {
        @case ('loading') {
          <ui-loading-state message="Loading your parcels…" />
        }
        @case ('error') {
          <ui-error-state title="Could not load your parcels" [message]="error()" (retry)="load()" />
        }
        @default {
          <section class="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3" aria-label="Parcel summary">
            <ui-card>
              <p class="text-sm text-ink-muted">All parcels</p>
              <p class="mt-1 text-3xl font-bold text-ink" data-testid="stat-total">{{ stats().total }}</p>
            </ui-card>
            <ui-card>
              <p class="text-sm text-ink-muted">Out for delivery</p>
              <p class="mt-1 text-3xl font-bold text-info-700" data-testid="stat-out">{{ stats().outForDelivery }}</p>
            </ui-card>
            <ui-card>
              <p class="text-sm text-ink-muted">Delivered</p>
              <p class="mt-1 text-3xl font-bold text-success-700" data-testid="stat-delivered">{{ stats().delivered }}</p>
            </ui-card>
          </section>

          <ui-card title="Recent parcels" [padded]="false">
            @if (recent().length === 0) {
              <ui-empty-state title="No parcels yet" message="Send your first parcel and it will show up here.">
                <ui-button routerLink="/customer">New delivery</ui-button>
              </ui-empty-state>
            } @else {
              <ui-table
                [columns]="columns"
                [rows]="recent()"
                [rowKey]="rowKey"
                caption="Your most recent parcels"
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
              <div class="border-t border-surface-border px-4 py-3 text-right">
                <a routerLink="/customer/parcels" class="text-sm font-medium text-brand-700 hover:underline">
                  View all parcels
                </a>
              </div>
            }
          </ui-card>
        }
      }
    </ui-shell>
  `,
})
export class CustomerDashboardComponent implements OnInit {
  private readonly parcels = inject(CustomerParcelsService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  static readonly RECENT_COUNT = 5;

  protected readonly nav = CUSTOMER_NAV;
  protected readonly columns = PARCEL_COLUMNS;
  protected readonly rowKey = parcelKey;

  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly error = signal('');
  readonly recent = signal<CustomerParcel[]>([]);
  readonly stats = signal<DashboardStats>({ total: 0, outForDelivery: 0, delivered: 0 });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    // size=1 is enough for the counts: only totalElements is used.
    forkJoin({
      recent: this.parcels.list({ page: 0, size: CustomerDashboardComponent.RECENT_COUNT }),
      outForDelivery: this.parcels.list({ page: 0, size: 1, status: 'OUT_FOR_DELIVERY' }),
      delivered: this.parcels.list({ page: 0, size: 1, status: 'DELIVERED' }),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ recent, outForDelivery, delivered }) => {
          this.recent.set(recent.content);
          this.stats.set({
            total: recent.totalElements,
            outForDelivery: outForDelivery.totalElements,
            delivered: delivered.totalElements,
          });
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
