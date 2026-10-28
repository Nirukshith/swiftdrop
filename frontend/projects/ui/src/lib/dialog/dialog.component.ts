import { Component, ElementRef, effect, input, model, output, viewChild } from '@angular/core';

let nextDialogId = 0;

/**
 * Modal dialog. Two-way bind [(open)]. Closes on Escape, on the backdrop (unless
 * [dismissible]="false") and on the close button; emits (closed) each time.
 * Put buttons in the `[uiDialogFooter]` slot.
 */
@Component({
  selector: 'ui-dialog',
  standalone: true,
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    @if (open()) {
      <div class="fixed inset-0 z-overlay flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-neutral-900/50" data-testid="ui-dialog-backdrop" (click)="dismissible() && close()"></div>
        <div
          #panel
          role="dialog"
          aria-modal="true"
          [attr.aria-labelledby]="titleId"
          tabindex="-1"
          class="relative w-full rounded-card bg-surface shadow-overlay focus:outline-none"
          [class.max-w-md]="size() === 'sm'"
          [class.max-w-lg]="size() === 'md'"
          [class.max-w-2xl]="size() === 'lg'"
        >
          <header class="flex items-start justify-between gap-4 border-b border-surface-border px-5 py-4">
            <h2 [id]="titleId" class="text-base font-semibold text-ink">{{ title() }}</h2>
            <button type="button" class="rounded-control p-1 text-ink-muted hover:bg-surface-muted hover:text-ink" aria-label="Close" (click)="close()">✕</button>
          </header>
          <div class="px-5 py-4 text-sm text-ink"><ng-content /></div>
          <footer class="flex justify-end gap-2 border-t border-surface-border px-5 py-3 empty:hidden"><ng-content select="[uiDialogFooter]" /></footer>
        </div>
      </div>
    }
  `,
})
export class DialogComponent {
  readonly open = model(false);
  readonly title = input.required<string>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly dismissible = input(true);
  readonly closed = output<void>();

  protected readonly titleId = `ui-dialog-title-${nextDialogId++}`;
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');

  constructor() {
    // Move focus into the dialog when it opens.
    effect(() => this.panel()?.nativeElement.focus());
  }

  close(): void {
    if (this.open()) {
      this.open.set(false);
      this.closed.emit();
    }
  }

  protected onEscape(): void {
    if (this.dismissible()) {
      this.close();
    }
  }
}
