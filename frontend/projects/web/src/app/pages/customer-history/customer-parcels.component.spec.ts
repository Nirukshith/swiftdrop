import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { CustomerParcelsComponent } from './customer-parcels.component';
import { CustomerParcel, ParcelListQuery, ParcelPage } from './customer-history.model';
import { CustomerParcelsService } from './customer-parcels.service';
import { pageOf, parcel } from './testing';
import { ReceiptService } from '../tracking/receipt.service';

describe('CustomerParcelsComponent', () => {
  let fixture: ComponentFixture<CustomerParcelsComponent>;
  let list: jest.Mock<Observable<ParcelPage>, [ParcelListQuery]>;

  /** 23 parcels, served 10 per page with the optional status filter, like the API. */
  const all: CustomerParcel[] = Array.from({ length: 23 }, (_, i) => parcel(i + 1, i % 3 === 0 ? 'DELIVERED' : 'IN_TRANSIT'));

  function render(): HTMLElement {
    fixture = TestBed.createComponent(CustomerParcelsComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const select = (el: HTMLElement) => el.querySelector('select') as HTMLSelectElement;

  beforeEach(() => {
    list = jest.fn((query: ParcelListQuery) => {
      const matching = all.filter((p) => !query.status || p.status === query.status);
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      return of(pageOf(matching.slice(page * size, page * size + size), matching.length, page, size));
    });
    TestBed.configureTestingModule({
      imports: [CustomerParcelsComponent],
      providers: [
        provideRouter([]),
        { provide: CustomerParcelsService, useValue: { list } },
        { provide: ReceiptService, useValue: { download } },
      ],
    });
  });

  const download = jest.fn(() => of('receipt.pdf'));

  it('loads the first page of 10 and shows paging', () => {
    const el = render();

    expect(list).toHaveBeenCalledWith({ page: 0, size: 10, status: null });
    expect(el.querySelectorAll('tbody tr')).toHaveLength(10);
    expect(el.textContent).toContain('23 parcels');
    expect(el.querySelector('ui-pagination')).not.toBeNull();
  });

  it('moves to another page', () => {
    const el = render();

    fixture.componentInstance.goToPage(2);
    fixture.detectChanges();

    expect(list).toHaveBeenLastCalledWith({ page: 2, size: 10, status: null });
    expect(el.querySelectorAll('tbody tr')).toHaveLength(3);
    expect(el.querySelector('tbody tr')?.textContent).toContain('SD-20261028-TST021');
  });

  it('filters by status from the select and goes back to the first page', () => {
    const el = render();
    fixture.componentInstance.goToPage(1);
    fixture.detectChanges();

    const options = Array.from(select(el).options).map((o) => o.textContent?.trim());
    expect(options).toEqual([
      'All statuses', 'Created', 'Picked up', 'In transit', 'Out for delivery', 'Delivered', 'Failed', 'Cancelled',
    ]);

    select(el).selectedIndex = 5; // Delivered
    select(el).dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(list).toHaveBeenLastCalledWith({ page: 0, size: 10, status: 'DELIVERED' });
    expect(el.textContent).toContain('8 parcels');
    expect(el.querySelector('ui-pagination')).toBeNull();
    el.querySelectorAll('tbody tr').forEach((row) => expect(row.textContent).toContain('Delivered'));
  });

  it('shows a filter-specific empty message when a status has no parcels', () => {
    const el = render();
    fixture.componentInstance.filterByStatus('CANCELLED');
    fixture.detectChanges();

    expect(el.textContent).toContain('No parcels with this status');
    expect(el.textContent).toContain('0 parcels');
  });

  it('shows the first-parcel empty state when the customer has no parcels at all', () => {
    list.mockImplementation(() => of(pageOf([], 0)));
    const el = render();

    expect(el.textContent).toContain('No parcels yet');
    expect(el.querySelector('table')).toBeNull();
  });

  it('shows the error and retries', () => {
    list.mockImplementation(() => throwError(() => new HttpErrorResponse({ status: 0 })));
    const el = render();
    expect(el.textContent).toContain('Cannot reach the server');

    list.mockImplementation(() => of(pageOf([parcel(1)], 1)));
    (el.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(el.textContent).toContain('1 parcel');
  });

  it('opens the parcel detail page when a row is clicked', () => {
    const el = render();
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (el.querySelector('tbody tr') as HTMLElement).click();

    expect(navigate).toHaveBeenCalledWith(['/customer/parcels', 'p-1']);
  });

  it('offers a receipt download only on delivered rows, without opening the row', () => {
    const el = render();
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    download.mockClear();

    const rows = Array.from(el.querySelectorAll('tbody tr'));
    const withReceipt = rows.filter((row) => row.querySelector('[data-testid="receipt-button"]'));
    // Parcels 1, 4, 7 and 10 on the first page are DELIVERED.
    expect(withReceipt.map((row) => row.textContent)).toEqual([
      expect.stringContaining('TST001'),
      expect.stringContaining('TST004'),
      expect.stringContaining('TST007'),
      expect.stringContaining('TST010'),
    ]);
    expect(el.querySelector('thead')?.textContent).toContain('Receipt');

    (withReceipt[1].querySelector('[data-testid="receipt-button"] button') as HTMLButtonElement).click();

    expect(download).toHaveBeenCalledWith('p-4');
    expect(navigate).not.toHaveBeenCalled();
  });

  // ---------------------------------------------------------------- phone layout

  it('also renders one card per parcel for small screens, with the table hidden there', () => {
    const el = render();

    const cards = Array.from(el.querySelectorAll('[data-testid="parcel-card"]'));
    expect(cards).toHaveLength(10);
    expect(el.querySelector('[data-testid="parcel-cards"]')?.classList).toContain('sm:hidden');
    expect(el.querySelector('ui-table')?.classList).toContain('hidden');
    expect(el.querySelector('ui-table')?.classList).toContain('sm:block');

    const first = cards[0];
    expect(first.textContent).toContain('SD-20261028-TST001');
    expect(first.textContent).toContain('To Recipient 1');
    expect(first.textContent).toContain('Kandy, Kandy');
    expect(first.querySelector('a')?.getAttribute('href')).toBe('/customer/parcels/p-1');
    // Parcel 1 is delivered, parcel 2 is not
    expect(first.querySelector('[data-testid="receipt-button"]')).not.toBeNull();
    expect(cards[1].querySelector('[data-testid="receipt-button"]')).toBeNull();
  });

  it('cards show the loading and filtered-empty states too', () => {
    const pending = new Subject<ParcelPage>();
    list.mockImplementation(() => pending);
    const el = render();
    expect(el.querySelector('[data-testid="parcel-cards"]')?.textContent).toContain('Loading your parcels');

    pending.next(pageOf([], 0));
    fixture.componentInstance.status.set('CANCELLED');
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="parcel-cards"]')?.textContent).toContain('No parcels with this status');
  });

  // ---------------------------------------------------------------- page and filter in the URL

  it('keeps the page and filter in the URL so Back from a parcel returns to the same place', () => {
    render();
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    fixture.componentInstance.goToPage(2);
    expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: { page: 3, status: null },
      replaceUrl: true,
    }));

    fixture.componentInstance.filterByStatus('DELIVERED');
    expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: { page: null, status: 'DELIVERED' },
    }));
  });
});

describe('CustomerParcelsComponent opened from a URL with page and filter', () => {
  function openWith(params: Record<string, string>) {
    const list = jest.fn((query: ParcelListQuery) => of(pageOf([parcel(1, 'DELIVERED')], 30, query.page ?? 0, 10)));
    TestBed.configureTestingModule({
      imports: [CustomerParcelsComponent],
      providers: [
        provideRouter([]),
        { provide: CustomerParcelsService, useValue: { list } },
        { provide: ReceiptService, useValue: { download: jest.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(params) } } },
      ],
    });
    const fixture = TestBed.createComponent(CustomerParcelsComponent);
    fixture.detectChanges();
    return { fixture, list };
  }

  it('starts on the page and status from the URL', async () => {
    const { fixture, list } = openWith({ page: '3', status: 'DELIVERED' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(list).toHaveBeenCalledWith({ page: 2, size: 10, status: 'DELIVERED' });
    expect(fixture.componentInstance.page()).toBe(2);
    const select = (fixture.nativeElement as HTMLElement).querySelector('select') as HTMLSelectElement;
    expect(select.selectedOptions[0]?.textContent?.trim()).toBe('Delivered');
  });

  it('ignores values in the URL that make no sense', () => {
    const { list } = openWith({ page: '-4', status: 'LOST' });

    expect(list).toHaveBeenCalledWith({ page: 0, size: 10, status: null });
  });
});
