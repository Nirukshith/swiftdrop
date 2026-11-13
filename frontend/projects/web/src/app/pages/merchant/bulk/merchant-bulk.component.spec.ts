import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MerchantBulkComponent } from './merchant-bulk.component';
import { CSV_HEADER, MAX_CSV_BYTES } from './merchant-csv';
import { TextDecoder } from 'util';

const row = (ref: string) => `${ref},Asha,+94771234567,10 Main Road,,Colombo,Colombo,00100,20 Lake Road,,Gampaha,Gampaha,11000,SMALL,1.5`;
describe('Merchant bulk upload', () => {
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MerchantBulkComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    jest.spyOn(crypto, 'randomUUID').mockReturnValue('00000000-0000-4000-8000-000000000001');
  });
  afterEach(() => { http.verify(); jest.restoreAllMocks(); });

  it('previews valid and invalid rows without HTTP and submits only confirmed valid rows', () => {
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    c.preview(CSV_HEADER + '\nshort,row\n' + row('good')); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1 valid / 1 invalid');
    expect(fixture.nativeElement.textContent).toContain('15 columns');
    http.expectNone('/api/v1/merchant/orders/bulk');
    c.confirm();
    const request = http.expectOne('/api/v1/merchant/orders/bulk');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Content-Type')).toBe('text/csv');
    expect(request.request.headers.get('Idempotency-Key')).toBe(c.uploadKey());
    expect(request.request.body).toContain('good'); expect(request.request.body).not.toContain('short,row');
    request.flush({ totalRows: 1, acceptedRows: 1, rejectedRows: 0, results: [{ rowNumber: 2, accepted: true, order: { trackingNumber: 'SD-real', status: 'CREATED', fee: 850 }, errors: [] }] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Source row 3');
    expect(fixture.nativeElement.textContent).toContain('SD-real');
    expect(fixture.nativeElement.textContent).toContain('850.00 LKR');
    fixture.destroy();
  });
  it('retains the exact key and body after a lost response, blocks double confirmation and shows row failures', () => {
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    c.preview(CSV_HEADER + '\n' + row('one')); c.confirm(); c.confirm();
    const original = http.expectOne('/api/v1/merchant/orders/bulk');
    const body = original.request.body, key = original.request.headers.get('Idempotency-Key');
    original.error(new ProgressEvent('error'));
    expect(c.error()).toContain('same reference');
    c.uploadKey.set('changed-after-confirmation');
    c.retry();
    const replay = http.expectOne('/api/v1/merchant/orders/bulk');
    expect(replay.request.body).toBe(body); expect(replay.request.headers.get('Idempotency-Key')).toBe(key);
    replay.flush({ totalRows: 1, acceptedRows: 0, rejectedRows: 1, results: [{ rowNumber: 2, accepted: false, errors: ['Outcome unknown; do not resubmit.'] }] });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Outcome unknown');
    fixture.destroy();
  });
  it('supports removing invalid rows, blocks invalid-only confirmation and exposes the documented template', () => {
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    c.preview(CSV_HEADER + '\nshort,row'); c.confirm();
    http.expectNone('/api/v1/merchant/orders/bulk');
    c.preview(CSV_HEADER + '\nshort,row\n' + row('valid')); c.removeInvalid();
    expect(c.rows()).toHaveLength(1); expect(c.rows()[0].rowNumber).toBe(3);
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('[download]').getAttribute('href')).toContain(encodeURIComponent(CSV_HEADER));
    fixture.destroy();
  });
  it('rejects oversized files before reading and clears private preview data on reset', () => {
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    c.loadFile({ target: { files: [{ size: MAX_CSV_BYTES + 1 }] } } as unknown as Event);
    expect(c.error()).toContain('5 MiB'); expect(c.rows()).toEqual([]);
    c.preview(CSV_HEADER + '\n' + row('private')); c.reset();
    expect(c.rows()).toEqual([]); expect(c.uploadKey()).toBe(''); expect(c.submittedCsv).toBeNull();
    fixture.destroy();
  });
  it('reads an uploaded file as strict UTF-8 and rejects malformed encoding before submission', async () => {
    Object.defineProperty(globalThis, 'TextDecoder', { value: TextDecoder, configurable: true });
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    const read = FileReader.prototype.readAsArrayBuffer;
    let complete: () => void;
    jest.spyOn(FileReader.prototype, 'readAsArrayBuffer').mockImplementation(function (this: FileReader, blob: Blob) {
      this.addEventListener('loadend', () => complete(), { once: true });
      read.call(this, blob);
    });
    const load = (file: File) => new Promise<void>(resolve => {
      complete = resolve;
      c.loadFile({ target: { files: [file] } } as unknown as Event);
    });
    await load(new File([CSV_HEADER + '\n' + row('uploaded')], 'orders.csv'));
    expect(c.validRows()).toHaveLength(1); expect(c.reading()).toBe(false);
    c.reset();
    await load(new File([new Uint8Array([0xc3, 0x28])], 'bad.csv'));
    expect(c.error()).toContain('UTF-8'); expect(c.rows()).toEqual([]);
    http.expectNone('/api/v1/merchant/orders/bulk');
    fixture.destroy();
  });
  it('displays authorization and rate-limit failures without retrying automatically', () => {
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    c.preview(CSV_HEADER + '\n' + row('one')); c.confirm();
    http.expectOne('/api/v1/merchant/orders/bulk').flush({ message: 'An approved merchant is required' }, { status: 403, statusText: 'Forbidden' });
    expect(c.error()).toContain('approved merchant');
    c.retry();
    http.expectOne('/api/v1/merchant/orders/bulk').flush({ message: 'Maximum five uploads' }, { status: 429, statusText: 'Too Many Requests' });
    expect(c.error()).toContain('five uploads'); expect(c.busy()).toBe(false);
    fixture.destroy();
  });
  it('clears the file control so the same corrected CSV can be selected again', () => {
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('#csv-file') as HTMLInputElement;
    const clear = jest.spyOn(input, 'value', 'set');
    c.preview(CSV_HEADER + '\n' + row('same-file'));
    c.reset();
    expect(clear).toHaveBeenCalledWith('');
    expect(input.disabled).toBe(false);
    expect(c.rows()).toEqual([]);
    http.expectNone('/api/v1/merchant/orders/bulk');
    fixture.destroy();
  });
  it('discards a late read from a file replaced by another selection', () => {
    Object.defineProperty(globalThis, 'TextDecoder', { value: TextDecoder, configurable: true });
    const fixture = TestBed.createComponent(MerchantBulkComponent), c = fixture.componentInstance;
    const readers: FileReader[] = [];
    jest.spyOn(FileReader.prototype, 'readAsArrayBuffer').mockImplementation(function (this: FileReader) { readers.push(this); });
    const load = (name: string) => c.loadFile({ target: { files: [new File(['data'], name)] } } as unknown as Event);
    load('old.csv'); load('current.csv');
    const finish = (reader: FileReader, ref: string) => {
      Object.defineProperty(reader, 'result', { value: new Uint8Array(Buffer.from(CSV_HEADER + '\n' + row(ref))).buffer });
      reader.onload?.call(reader, new ProgressEvent('load') as ProgressEvent<FileReader>);
    };
    finish(readers[1], 'current'); finish(readers[0], 'stale');
    expect(c.rows()[0].cells[0]).toBe('current');
    expect(c.reading()).toBe(false);
    http.expectNone('/api/v1/merchant/orders/bulk');
    fixture.destroy();
  });
});
