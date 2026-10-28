import { Component, Directive, TemplateRef, contentChildren, computed, inject, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { EmptyStateComponent, LoadingStateComponent } from '../states/states.component';

export interface TableColumn<T> {
  /** Unique key, also used to match a `uiCell` template. */
  key: string;
  header: string;
  /** Plain-text value. Defaults to `row[key]`. Ignored when a `uiCell` template exists. */
  value?: (row: T) => unknown;
  align?: 'left' | 'right';
}

/** Custom cell: `<ng-template uiCell="status" let-row>...</ng-template>` */
@Directive({ selector: 'ng-template[uiCell]', standalone: true })
export class TableCellDirective {
  readonly uiCell = input.required<string>();
  readonly template = inject<TemplateRef<{ $implicit: unknown }>>(TemplateRef);
}

/** Data table with header, custom cells, row click, and built-in loading and empty states. */
@Component({
  selector: 'ui-table',
  standalone: true,
  imports: [NgTemplateOutlet, LoadingStateComponent, EmptyStateComponent],
  host: { class: 'block' },
  template: `
    <div class="overflow-x-auto">
      <table class="min-w-full divide-y divide-surface-border text-sm">
        @if (caption()) {
          <caption class="sr-only">{{ caption() }}</caption>
        }
        <thead class="bg-surface-muted">
          <tr>
            @for (column of columns(); track column.key) {
              <th
                scope="col"
                class="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-ink-muted"
                [class.text-right]="column.align === 'right'"
                [class.text-left]="column.align !== 'right'"
              >
                {{ column.header }}
              </th>
            }
          </tr>
        </thead>
        <tbody class="divide-y divide-surface-border bg-surface">
          @if (loading()) {
            <tr>
              <td [attr.colspan]="columns().length"><ui-loading-state [message]="loadingMessage()" /></td>
            </tr>
          } @else {
            @for (row of rows(); track trackRow($index, row)) {
              <tr
                [class]="clickable() ? clickableRowClasses : ''"
                [attr.tabindex]="clickable() ? 0 : null"
                (click)="clickable() && rowClick.emit(row)"
                (keydown.enter)="clickable() && rowClick.emit(row)"
              >
                @for (column of columns(); track column.key) {
                  <td class="whitespace-nowrap px-4 py-3 text-ink" [class.text-right]="column.align === 'right'">
                    @if (cellTemplates().get(column.key); as template) {
                      <ng-container [ngTemplateOutlet]="template" [ngTemplateOutletContext]="{ $implicit: row }" />
                    } @else {
                      {{ cellValue(column, row) }}
                    }
                  </td>
                }
              </tr>
            } @empty {
              <tr>
                <td [attr.colspan]="columns().length">
                  <ui-empty-state [title]="emptyTitle()" [message]="emptyMessage()" />
                </td>
              </tr>
            }
          }
        </tbody>
      </table>
    </div>
  `,
})
export class TableComponent<T> {
  readonly columns = input.required<TableColumn<T>[]>();
  readonly rows = input<T[]>([]);
  readonly loading = input(false);
  readonly loadingMessage = input('Loading…');
  readonly emptyTitle = input('No results');
  readonly emptyMessage = input('');
  readonly caption = input('');
  /** Makes rows focusable and emits (rowClick) on click or Enter. */
  readonly clickable = input(false);
  /** Stable identity for rows, e.g. `row => row.id`. Defaults to the index. */
  readonly rowKey = input<((row: T) => unknown) | null>(null);
  readonly rowClick = output<T>();

  protected readonly clickableRowClasses = 'cursor-pointer hover:bg-surface-muted focus:bg-surface-muted focus:outline-none';

  private readonly cellDefs = contentChildren(TableCellDirective);
  protected readonly cellTemplates = computed(
    () => new Map(this.cellDefs().map((def) => [def.uiCell(), def.template] as const)),
  );

  protected trackRow(index: number, row: T): unknown {
    const key = this.rowKey();
    return key ? key(row) : index;
  }

  protected cellValue(column: TableColumn<T>, row: T): unknown {
    if (column.value) {
      return column.value(row);
    }
    return (row as Record<string, unknown>)[column.key] ?? '';
  }
}
