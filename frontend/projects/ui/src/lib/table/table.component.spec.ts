import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TableCellDirective, TableColumn, TableComponent } from './table.component';

interface Row {
  id: string;
  trackingNumber: string;
  fee: number;
  status: string;
}

@Component({
  standalone: true,
  imports: [TableComponent, TableCellDirective],
  template: `
    <ui-table
      [columns]="columns"
      [rows]="rows()"
      [loading]="loading()"
      [clickable]="true"
      [rowKey]="rowKey"
      emptyTitle="No parcels"
      (rowClick)="clicked = $event"
    >
      <ng-template uiCell="status" let-row><b class="status">{{ row.status }}!</b></ng-template>
    </ui-table>
  `,
})
class HostComponent {
  columns: TableColumn<Row>[] = [
    { key: 'trackingNumber', header: 'Tracking no.' },
    { key: 'fee', header: 'Fee', align: 'right', value: (row) => `LKR ${row.fee}` },
    { key: 'status', header: 'Status' },
  ];
  rows = signal<Row[]>([
    { id: 'a', trackingNumber: 'SD-20261026-A1B2C3', fee: 750, status: 'CREATED' },
    { id: 'b', trackingNumber: 'SD-20261026-ZZ9900', fee: 500, status: 'DELIVERED' },
  ]);
  loading = signal(false);
  rowKey = (row: Row) => row.id;
  clicked: Row | null = null;
}

describe('TableComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return { fixture, host: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('renders headers, values, value functions and custom cells', () => {
    const { el } = setup();
    const headers = Array.from(el.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Tracking no.', 'Fee', 'Status']);
    const firstRow = el.querySelectorAll('tbody tr')[0];
    expect(firstRow.textContent).toContain('SD-20261026-A1B2C3');
    expect(firstRow.textContent).toContain('LKR 750');
    expect(firstRow.querySelector('.status')?.textContent).toBe('CREATED!');
  });

  it('emits the row on click and on Enter', () => {
    const { host, el } = setup();
    const secondRow = el.querySelectorAll('tbody tr')[1] as HTMLElement;
    secondRow.click();
    expect(host.clicked?.id).toBe('b');

    host.clicked = null;
    secondRow.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(host.clicked).not.toBeNull();
    expect(secondRow.getAttribute('tabindex')).toBe('0');
  });

  it('shows the empty state without rows', () => {
    const { fixture, host, el } = setup();
    host.rows.set([]);
    fixture.detectChanges();
    expect(el.querySelector('ui-empty-state')?.textContent).toContain('No parcels');
  });

  it('shows the loading state instead of rows', () => {
    const { fixture, host, el } = setup();
    host.loading.set(true);
    fixture.detectChanges();
    expect(el.querySelector('ui-loading-state')).not.toBeNull();
    expect(el.textContent).not.toContain('SD-20261026-A1B2C3');
  });
});
