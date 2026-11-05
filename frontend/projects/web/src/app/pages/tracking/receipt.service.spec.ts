import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReceiptService, SAVE_FILE, fileNameFrom, receiptErrorMessage } from './receipt.service';

describe('ReceiptService', () => {
  let http: HttpTestingController;
  let service: ReceiptService;
  const save = jest.fn<void, [Blob, string]>();

  beforeEach(() => {
    save.mockReset();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SAVE_FILE, useValue: save }],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ReceiptService);
  });

  afterEach(() => http.verify());

  it('downloads the PDF from the contract URL and saves it under the server file name', () => {
    let saved: string | undefined;
    service.download('p 1').subscribe((name) => (saved = name));

    const request = http.expectOne('/api/v1/parcels/p%201/receipt');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.headers.get('Accept')).toBe('application/pdf');
    const pdf = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    request.flush(pdf, { headers: { 'Content-Disposition': 'attachment; filename="receipt-SD-20261028-RCP001.pdf"' } });

    expect(saved).toBe('receipt-SD-20261028-RCP001.pdf');
    expect(save).toHaveBeenCalledWith(pdf, 'receipt-SD-20261028-RCP001.pdf');
  });

  it('does not save anything when the server refuses the download', () => {
    let error: unknown;
    service.download('p-1').subscribe({ error: (e: unknown) => (error = e) });

    http.expectOne('/api/v1/parcels/p-1/receipt').flush(new Blob(['{}']), { status: 409, statusText: 'Conflict' });

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect(save).not.toHaveBeenCalled();
  });

  it('reads the file name from Content-Disposition and never accepts a path', () => {
    expect(fileNameFrom('attachment; filename="receipt-SD-20261028-A1B2C3.pdf"')).toBe('receipt-SD-20261028-A1B2C3.pdf');
    expect(fileNameFrom('attachment; filename=receipt-SD-1.pdf')).toBe('receipt-SD-1.pdf');
    expect(fileNameFrom(null)).toBe('receipt.pdf');
    expect(fileNameFrom('attachment; filename="../../evil.pdf"')).toBe('receipt.pdf');
    expect(fileNameFrom('attachment; filename="receipt.exe"')).toBe('receipt.pdf');
  });

  it('maps each contract error to a clear message', () => {
    const error = (status: number) => new HttpErrorResponse({ status });
    expect(receiptErrorMessage(error(401))).toContain('sign in again');
    expect(receiptErrorMessage(error(403))).toBe('You do not have access to this receipt.');
    expect(receiptErrorMessage(error(404))).toBe('This parcel could not be found.');
    expect(receiptErrorMessage(error(409))).toBe('The receipt is available once the parcel has been delivered.');
    expect(receiptErrorMessage(error(0))).toContain('Cannot reach the server');
    expect(receiptErrorMessage(error(500))).toBe('The receipt could not be downloaded. Please try again.');
  });
});
