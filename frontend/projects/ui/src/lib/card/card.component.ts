import { Component, input } from '@angular/core';

/**
 * Surface for grouping content. Optional slots:
 * `[uiCardActions]` (header, right side) and `[uiCardFooter]`.
 */
@Component({
  selector: 'ui-card',
  standalone: true,
  host: { class: 'block' },
  template: `
    <section class="rounded-card border border-surface-border bg-surface shadow-card">
      @if (title()) {
        <header class="flex items-start justify-between gap-4 border-b border-surface-border px-5 py-4">
          <div>
            <h2 class="text-base font-semibold text-ink">{{ title() }}</h2>
            @if (subtitle()) {
              <p class="mt-0.5 text-sm text-ink-muted">{{ subtitle() }}</p>
            }
          </div>
          <div class="flex shrink-0 items-center gap-2 empty:hidden"><ng-content select="[uiCardActions]" /></div>
        </header>
      }
      <div [class.p-5]="padded()"><ng-content /></div>
      <footer class="border-t border-surface-border px-5 py-3 empty:hidden"><ng-content select="[uiCardFooter]" /></footer>
    </section>
  `,
})
export class CardComponent {
  readonly title = input('');
  readonly subtitle = input('');
  /** Set false for edge-to-edge content such as tables. */
  readonly padded = input(true);
}
