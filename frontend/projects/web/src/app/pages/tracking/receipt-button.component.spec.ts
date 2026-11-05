import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ReceiptButtonComponent } from './receipt-button.component';
import { ReceiptService } from './receipt.service';

@Component({
  standalone: true,
  imports: [ReceiptButtonComponent],
  template: `<div (click)="rowClicks = rowClicks + 1"><app-receipt-button parcelId="p-1" trackingNumber="SD-20261028-RCP001" /></div>`,
})
class HostComponent {
  rowClicks = 0;
}

describe('ReceiptButtonComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let result: Subject<string>;
  const download = jest.fn();

  beforeEach(() => {
    download.mockReset();
    download.mockImplementation(() => {
      result = new Subject<string>();
      return result;
    });
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [{ provide: ReceiptService, useValue: { download } }],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const button = () => el().querySelector('[data-testid="receipt-button"] button') as HTMLButtonElement;
  const error = () => el().querySelector('[data-testid="receipt-error"]')?.textContent?.trim();

  it('names the parcel for screen readers', () => {
    expect(button().textContent).toContain('Download receipt');
    expect(button().textContent).toContain('for SD-20261028-RCP001');
  });

  it('downloads the receipt for its parcel, once, while busy', () => {
    button().click();
    button().click();
    fixture.detectChanges();

    expect(download).toHaveBeenCalledTimes(1);
    expect(download).toHaveBeenCalledWith('p-1');

    result.next('receipt-SD-20261028-RCP001.pdf');
    result.complete();
    fixture.detectChanges();
    expect(error()).toBeUndefined();

    button().click();
    expect(download).toHaveBeenCalledTimes(2);
  });

  it('shows why the download failed and lets the user try again', () => {
    button().click();
    result.error(new HttpErrorResponse({ status: 409 }));
    fixture.detectChanges();

    expect(error()).toBe('The receipt is available once the parcel has been delivered.');
    expect(el().querySelector('[role="alert"]')).not.toBeNull();

    button().click();
    fixture.detectChanges();
    expect(error()).toBeUndefined();
    expect(download).toHaveBeenCalledTimes(2);
  });

  it('a click on the button does not also open the table row', () => {
    button().click();
    expect(fixture.componentInstance.rowClicks).toBe(0);
  });
});
