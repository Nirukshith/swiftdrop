import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { CustomerParcelsComponent } from './customer-parcels.component';
import { CustomerParcel, ParcelListQuery, ParcelPage } from './customer-history.model';
import { CustomerParcelsService } from './customer-parcels.service';
import { pageOf, parcel } from './testing';

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
      providers: [provideRouter([]), { provide: CustomerParcelsService, useValue: { list } }],
    });
  });

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

  it('opens the tracking page when a row is clicked', () => {
    const el = render();
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (el.querySelector('tbody tr') as HTMLElement).click();

    expect(navigate).toHaveBeenCalledWith(['/track', 'SD-20261028-TST001']);
  });
});
