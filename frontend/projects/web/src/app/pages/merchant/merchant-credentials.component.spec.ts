import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MerchantCredentialsComponent } from './merchant-credentials.component';
import { MerchantApiCredentials } from './merchant.model';

describe('MerchantCredentialsComponent', () => {
  let fixture: ComponentFixture<MerchantCredentialsComponent>;
  const credentials: MerchantApiCredentials = {
    merchantId: 'm-1', status: 'APPROVED', clientId: 'client-1',
    clientSecret: 'one-time-test-secret', secretAvailableOnce: true,
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MerchantCredentialsComponent] }).compileComponents();
    fixture = TestBed.createComponent(MerchantCredentialsComponent);
  });

  it('shows the supplied OAuth credentials and asks the owner to clear them after acknowledgement', () => {
    fixture.componentRef.setInput('credentials', credentials);
    const saved = jest.fn();
    fixture.componentInstance.secretSaved.subscribe(saved);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('client-1');
    expect(fixture.nativeElement.textContent).toContain('one-time-test-secret');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    expect(saved).toHaveBeenCalledTimes(1);
    fixture.componentRef.setInput('credentials', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('one-time-test-secret');
  });

  it('does not retrieve or redisplay secrets when reopened without an approval response', () => {
    fixture.componentRef.setInput('credentials', credentials);
    fixture.detectChanges();
    fixture.destroy();
    fixture = TestBed.createComponent(MerchantCredentialsComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('one-time-test-secret');
    expect(fixture.nativeElement.textContent).not.toContain('APPROVED');
    expect(fixture.nativeElement.textContent).toContain('administrator will securely hand over');
  });

  it('renders the public UI kit components with the approved status', () => {
    fixture.componentRef.setInput('credentials', credentials);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('ui-card')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('ui-status-badge').textContent).toContain('APPROVED');
    expect(fixture.nativeElement.querySelector('ui-button')).toBeTruthy();
  });

  it('shows pending approval without exposing credentials', () => {
    fixture.componentRef.setInput('credentials', { ...credentials, status: 'PENDING' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('awaiting administrator approval');
    expect(fixture.nativeElement.querySelector('ui-status-badge').textContent).toContain('PENDING');
    expect(fixture.nativeElement.textContent).not.toContain('one-time-test-secret');
    expect(fixture.nativeElement.querySelector('#clientId')).toBeNull();
  });

  it('explains unavailable secrets without claiming they have already been delivered', () => {
    fixture.componentRef.setInput('credentials', { ...credentials, clientSecret: null, secretAvailableOnce: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('secret is unavailable');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});
