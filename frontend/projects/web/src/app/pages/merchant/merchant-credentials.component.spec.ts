import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MerchantCredentialsComponent } from './merchant-credentials.component';
import { MerchantService } from './merchant.service';

describe('MerchantCredentialsComponent', () => {
  let service: MerchantService;
  let fixture: ComponentFixture<MerchantCredentialsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MerchantCredentialsComponent],
      providers: [MerchantService],
    }).compileComponents();
    service = TestBed.inject(MerchantService);
    fixture = TestBed.createComponent(MerchantCredentialsComponent);
    fixture.detectChanges();
  });

  it('shows mock OAuth credentials and hides the one-time secret after acknowledgement', () => {
    expect(fixture.nativeElement.textContent).toContain('swd_test_client_merchant_01');
    expect(fixture.nativeElement.textContent).toContain('swd_test_secret_once_7f3a9c2e');

    const saveButton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    saveButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('swd_test_secret_once_7f3a9c2e');
    expect(fixture.nativeElement.textContent).toContain('already been shown');
  });

  it('does not show the mock secret again when the page is reopened', () => {
    fixture.destroy();
    fixture = TestBed.createComponent(MerchantCredentialsComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('swd_test_secret_once_7f3a9c2e');
    expect(fixture.nativeElement.textContent).toContain('already been shown');
  });

  it('renders UI kit components: card, status badge, and button', () => {
    const card = fixture.nativeElement.querySelector('ui-card');
    expect(card).toBeTruthy();

    const badge = fixture.nativeElement.querySelector('ui-status-badge');
    expect(badge).toBeTruthy();
    expect(badge.textContent).toContain('APPROVED');

    const button = fixture.nativeElement.querySelector('ui-button');
    expect(button).toBeTruthy();
  });

  it('shows awaiting administrator approval message when status is PENDING', () => {
    jest.spyOn(service, 'getApiCredentials').mockReturnValue(of({
      merchantId: 'm-pending',
      status: 'PENDING',
      clientId: 'swd_test_client_pending',
      clientSecret: null,
      secretAvailableOnce: false,
    }));

    const pendingFixture = TestBed.createComponent(MerchantCredentialsComponent);
    pendingFixture.detectChanges();

    expect(pendingFixture.nativeElement.textContent).toContain('awaiting administrator approval');
    const badge = pendingFixture.nativeElement.querySelector('ui-status-badge');
    expect(badge?.textContent).toContain('PENDING');
  });
});
