import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { CustomerDashboardComponent } from './customer-dashboard.component';
import { ParcelListQuery, ParcelPage } from './customer-history.model';
import { CustomerParcelsService } from './customer-parcels.service';
import { pageOf, parcel } from './testing';

describe('CustomerDashboardComponent', () => {
  let fixture: ComponentFixture<CustomerDashboardComponent>;
  let list: jest.Mock<Observable<ParcelPage>, [ParcelListQuery]>;

  function render(): HTMLElement {
    fixture = TestBed.createComponent(CustomerDashboardComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (el: HTMLElement, testId: string) =>
    el.querySelector(`[data-testid="${testId}"]`)?.textContent?.trim();

  beforeEach(() => {
    list = jest.fn((query: ParcelListQuery) => {
      if (query.status === 'OUT_FOR_DELIVERY') return of(pageOf([parcel(9, 'OUT_FOR_DELIVERY')], 2));
      if (query.status === 'DELIVERED') return of(pageOf([parcel(8, 'DELIVERED')], 4));
      return of(pageOf([parcel(1), parcel(2, 'DELIVERED')], 7));
    });
    TestBed.configureTestingModule({
      imports: [CustomerDashboardComponent],
      providers: [provideRouter([]), { provide: CustomerParcelsService, useValue: { list } }],
    });
  });

  it('shows the counts and the recent parcels', () => {
    const el = render();

    expect(list).toHaveBeenCalledWith({ page: 0, size: 5 });
    expect(list).toHaveBeenCalledWith({ page: 0, size: 1, status: 'OUT_FOR_DELIVERY' });
    expect(list).toHaveBeenCalledWith({ page: 0, size: 1, status: 'DELIVERED' });
    expect(text(el, 'stat-total')).toBe('7');
    expect(text(el, 'stat-out')).toBe('2');
    expect(text(el, 'stat-delivered')).toBe('4');

    const rows = el.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('SD-20261028-TST001');
    expect(rows[0].textContent).toContain('In transit');
    expect(rows[0].textContent).toContain('Kandy, Kandy');
    expect(rows[1].textContent).toContain('Delivered');
    expect(el.querySelector('a[href="/customer/parcels"]')).not.toBeNull();
  });

  it('shows an empty state with a link to the wizard when there are no parcels', () => {
    list.mockImplementation(() => of(pageOf([], 0)));
    const el = render();

    expect(el.textContent).toContain('No parcels yet');
    expect(el.querySelector('table')).toBeNull();
    expect(text(el, 'stat-total')).toBe('0');
  });

  it('shows the API error message and retries', () => {
    list.mockImplementation(() =>
      throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Parcel service is down' } })),
    );
    const el = render();

    expect(el.textContent).toContain('Could not load your parcels');
    expect(el.textContent).toContain('Parcel service is down');

    list.mockImplementation(() => of(pageOf([parcel(1)], 1)));
    (el.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('opens the tracking page when a row is clicked', () => {
    const el = render();
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (el.querySelector('tbody tr') as HTMLElement).click();

    expect(navigate).toHaveBeenCalledWith(['/track', 'SD-20261028-TST001']);
  });
});
