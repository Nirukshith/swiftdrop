import { Component, contentChildren, effect, input, model, signal } from '@angular/core';

let nextTabsId = 0;

/** One panel inside <ui-tabs>. Only the selected panel is visible. */
@Component({
  selector: 'ui-tab',
  standalone: true,
  host: {
    role: 'tabpanel',
    '[id]': 'panelId()',
    '[attr.aria-labelledby]': 'tabId()',
    '[hidden]': '!active()',
    class: 'block pt-4',
  },
  template: `<ng-content />`,
})
export class TabComponent {
  readonly label = input.required<string>();
  readonly active = signal(false);
  readonly panelId = signal('');
  readonly tabId = signal('');
}

/**
 * Accessible tab list (WAI-ARIA tabs pattern, arrow keys move between tabs).
 * `<ui-tabs [(selectedIndex)]="tab"><ui-tab label="Active">…</ui-tab></ui-tabs>`
 */
@Component({
  selector: 'ui-tabs',
  standalone: true,
  host: { class: 'block' },
  template: `
    <div role="tablist" class="flex gap-1 border-b border-surface-border" [attr.aria-label]="ariaLabel() || null">
      @for (tab of tabs(); track $index) {
        <button
          type="button"
          role="tab"
          [id]="tab.tabId()"
          [attr.aria-controls]="tab.panelId()"
          [attr.aria-selected]="$index === selectedIndex()"
          [attr.tabindex]="$index === selectedIndex() ? 0 : -1"
          [class]="$index === selectedIndex() ? activeClasses : inactiveClasses"
          (click)="select($index)"
          (keydown)="onKeydown($event, $index)"
        >
          {{ tab.label() }}
        </button>
      }
    </div>
    <ng-content />
  `,
})
export class TabsComponent {
  readonly selectedIndex = model(0);
  readonly ariaLabel = input('');

  protected readonly tabs = contentChildren(TabComponent);
  private readonly id = nextTabsId++;

  protected readonly activeClasses =
    '-mb-px border-b-2 border-brand-600 px-4 py-2 text-sm font-medium text-brand-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500';
  protected readonly inactiveClasses =
    '-mb-px border-b-2 border-transparent px-4 py-2 text-sm font-medium text-ink-muted hover:text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500';

  constructor() {
    effect(() => {
      const selected = this.selectedIndex();
      this.tabs().forEach((tab, index) => {
        tab.tabId.set(`ui-tabs-${this.id}-tab-${index}`);
        tab.panelId.set(`ui-tabs-${this.id}-panel-${index}`);
        tab.active.set(index === selected);
      });
    });
  }

  protected select(index: number): void {
    this.selectedIndex.set(index);
  }

  protected onKeydown(event: KeyboardEvent, index: number): void {
    const count = this.tabs().length;
    let next: number | null = null;
    if (event.key === 'ArrowRight') next = (index + 1) % count;
    if (event.key === 'ArrowLeft') next = (index - 1 + count) % count;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = count - 1;
    if (next === null) return;
    event.preventDefault();
    this.select(next);
    const host = (event.target as HTMLElement).parentElement;
    (host?.children[next] as HTMLElement | undefined)?.focus();
  }
}
