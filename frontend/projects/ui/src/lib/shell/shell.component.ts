import { Component, input, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface ShellNavItem {
  label: string;
  link: string;
  /** Highlight only on an exact URL match (use for parent routes like `/customer`). */
  exact?: boolean;
}

/**
 * Page layout: top bar with brand, navigation and an actions slot, then the main content.
 * Navigation collapses into a menu button below the `md` breakpoint (360 px phones, NF-04).
 * `<ui-shell [navItems]="nav"><ng-container uiShellActions>…</ng-container> page </ui-shell>`
 */
@Component({
  selector: 'ui-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  host: { class: 'block min-h-screen bg-surface-muted text-ink' },
  template: `
    <header class="border-b border-surface-border bg-surface">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
        <a [routerLink]="homeLink()" class="text-lg font-bold text-brand-700">{{ appName() }}</a>
        <nav class="hidden items-center gap-1 md:flex" [attr.aria-label]="navLabel()">
          @for (item of navItems(); track item.link) {
            <a
              [routerLink]="item.link"
              routerLinkActive="bg-brand-50 text-brand-700"
              [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
              class="rounded-control px-3 py-2 text-sm font-medium text-ink-muted hover:text-ink"
              >{{ item.label }}</a
            >
          }
        </nav>
        <div class="flex items-center gap-2">
          <ng-content select="[uiShellActions]" />
          @if (navItems().length) {
            <button
              type="button"
              class="rounded-control p-2 text-ink-muted hover:bg-surface-muted md:hidden"
              [attr.aria-expanded]="menuOpen()"
              aria-controls="ui-shell-mobile-nav"
              aria-label="Toggle navigation"
              (click)="menuOpen.set(!menuOpen())"
            >
              ☰
            </button>
          }
        </div>
      </div>
      @if (menuOpen()) {
        <nav id="ui-shell-mobile-nav" class="border-t border-surface-border px-4 py-2 md:hidden" [attr.aria-label]="navLabel()">
          @for (item of navItems(); track item.link) {
            <a
              [routerLink]="item.link"
              routerLinkActive="bg-brand-50 text-brand-700"
              [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
              class="block rounded-control px-3 py-2 text-sm font-medium text-ink-muted"
              (click)="menuOpen.set(false)"
              >{{ item.label }}</a
            >
          }
        </nav>
      }
    </header>
    <main class="mx-auto max-w-6xl px-4 py-6">
      @if (pageTitle()) {
        <h1 class="mb-6 text-2xl font-bold text-ink">{{ pageTitle() }}</h1>
      }
      <ng-content />
    </main>
  `,
})
export class ShellComponent {
  readonly appName = input('SwiftDrop');
  readonly homeLink = input('/');
  readonly navItems = input<ShellNavItem[]>([]);
  readonly navLabel = input('Main');
  readonly pageTitle = input('');

  protected readonly menuOpen = signal(false);
}
