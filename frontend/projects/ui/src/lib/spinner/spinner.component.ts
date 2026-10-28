import { Component, input } from '@angular/core';

/** Small animated spinner. Decorative: pair it with visible or sr-only text. */
@Component({
  selector: 'ui-spinner',
  standalone: true,
  host: { class: 'inline-flex', 'aria-hidden': 'true' },
  template: `
    <svg
      class="animate-spin"
      [class.h-4]="size() === 'sm'"
      [class.w-4]="size() === 'sm'"
      [class.h-8]="size() === 'lg'"
      [class.w-8]="size() === 'lg'"
      viewBox="0 0 24 24"
      fill="none"
    >
      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path>
    </svg>
  `,
})
export class SpinnerComponent {
  readonly size = input<'sm' | 'lg'>('sm');
}
