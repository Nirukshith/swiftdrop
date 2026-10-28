import { Component, computed, input, output } from '@angular/core';

/**
 * Page navigation for the backend's 0-based paging (`page`, `size`, `totalElements`,
 * see ParcelPageResponse). Emits the new 0-based page index.
 */
@Component({
  selector: 'ui-pagination',
  standalone: true,
  host: { class: 'block' },
  template: `
    <nav class="flex flex-wrap items-center justify-between gap-3 text-sm" [attr.aria-label]="ariaLabel()">
      <p class="text-ink-muted">
        @if (totalElements() > 0) {
          Showing <span class="font-medium text-ink">{{ from() }}</span>–<span class="font-medium text-ink">{{ to() }}</span>
          of <span class="font-medium text-ink">{{ totalElements() }}</span>
        } @else {
          No results
        }
      </p>
      @if (totalPages() > 1) {
        <ul class="flex items-center gap-1">
          <li>
            <button type="button" [class]="navClasses" [disabled]="page() === 0" (click)="go(page() - 1)" aria-label="Previous page">‹</button>
          </li>
          @for (p of visiblePages(); track p) {
            <li>
              <button
                type="button"
                [class]="p === page() ? currentClasses : navClasses"
                [attr.aria-current]="p === page() ? 'page' : null"
                (click)="go(p)"
              >
                {{ p + 1 }}
              </button>
            </li>
          }
          <li>
            <button type="button" [class]="navClasses" [disabled]="page() >= totalPages() - 1" (click)="go(page() + 1)" aria-label="Next page">›</button>
          </li>
        </ul>
      }
    </nav>
  `,
})
export class PaginationComponent {
  readonly page = input(0);
  readonly size = input(20);
  readonly totalElements = input(0);
  /** How many page numbers to show at once. */
  readonly window = input(5);
  readonly ariaLabel = input('Pagination');
  readonly pageChange = output<number>();

  protected readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / Math.max(1, this.size()))));
  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min(this.totalElements(), (this.page() + 1) * this.size()));
  protected readonly visiblePages = computed(() => {
    const total = this.totalPages();
    const count = Math.min(this.window(), total);
    const start = Math.min(Math.max(0, this.page() - Math.floor(count / 2)), total - count);
    return Array.from({ length: count }, (_, i) => start + i);
  });

  protected readonly navClasses =
    'min-w-[2.25rem] rounded-control border border-surface-border bg-surface px-2.5 py-1.5 text-ink hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50';
  protected readonly currentClasses =
    'min-w-[2.25rem] rounded-control border border-brand-600 bg-brand-600 px-2.5 py-1.5 font-medium text-white';

  protected go(page: number): void {
    if (page >= 0 && page < this.totalPages() && page !== this.page()) {
      this.pageChange.emit(page);
    }
  }
}
