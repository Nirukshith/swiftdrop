import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { MerchantApprovalComponent } from './merchant-approval.component';
import { MerchantApprovalService } from './merchant-approval.service';
import { MerchantSummary } from './merchant-approval.model';

describe('MerchantApprovalComponent', () => {
  let component: MerchantApprovalComponent;
  let fixture: ComponentFixture<MerchantApprovalComponent>;
  let service: {
    merchants: jest.Mock;
    totalElements: jest.Mock;
    totalPages: jest.Mock;
    currentPage: jest.Mock;
    pageSize: jest.Mock;
    loading: jest.Mock;
    error: jest.Mock;
    refresh: jest.Mock;
    loadMerchants: jest.Mock;
    approve: jest.Mock;
    reject: jest.Mock;
    messageFor: jest.Mock;
  };
  const merchant: MerchantSummary = {
    id: 'm-1',
    businessName: 'North Star Stores',
    contactName: 'Asha Perera',
    email: 'asha@example.test',
    phoneNumber: '+94771234567',
    status: 'PENDING',
    rejectionReason: null,
    createdAt: '2026-11-01T10:00:00Z',
  };

  beforeEach(async () => {
    service = {
      merchants: jest.fn().mockReturnValue([merchant]),
      totalElements: jest.fn().mockReturnValue(1),
      totalPages: jest.fn().mockReturnValue(1),
      currentPage: jest.fn().mockReturnValue(0),
      pageSize: jest.fn().mockReturnValue(20),
      loading: jest.fn().mockReturnValue(false),
      error: jest.fn().mockReturnValue(null),
      refresh: jest.fn().mockReturnValue(of({ content: [merchant], totalElements: 1, totalPages: 1, page: 0, size: 20 })),
      loadMerchants: jest.fn().mockReturnValue(of({ content: [merchant], totalElements: 1, totalPages: 1, page: 0, size: 20 })),
      approve: jest.fn().mockReturnValue(of({ merchant: { ...merchant, status: 'APPROVED' }, clientId: 'client-1' })),
      reject: jest.fn().mockReturnValue(of({ ...merchant, status: 'REJECTED', rejectionReason: 'Not verified' })),
      messageFor: jest.fn().mockReturnValue('Action failed'),
    };

    await TestBed.configureTestingModule({
      imports: [MerchantApprovalComponent],
      providers: [provideRouter([]), { provide: MerchantApprovalService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(MerchantApprovalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads and displays pending merchants', () => {
    expect(service.refresh).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('North Star Stores');
    expect(fixture.nativeElement.textContent).toContain('PENDING');
  });

  it('displays approval credentials once, retains them across list refresh, and clears them on acknowledgement', () => {
    service.approve.mockReturnValueOnce(of({ merchant: { ...merchant, status: 'APPROVED' },
      clientId: 'client-1', clientSecret: 'one-time-test-secret' }));
    component.openApprove(merchant);
    component.confirmAction();
    fixture.detectChanges();
    expect(component.feedback()).toContain('APPROVED');
    expect(fixture.nativeElement.textContent).toContain('one-time-test-secret');
    expect(fixture.nativeElement.textContent).toContain('Credentials for North Star Stores');
    component.reload();
    component.openApprove({ ...merchant, id: 'm-2' });
    expect(component.action()).toBeNull();
    expect(service.approve).toHaveBeenCalledTimes(1);
    const handoff = fixture.nativeElement.querySelector('app-merchant-credentials');
    (handoff.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.handoff()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('one-time-test-secret');
    component.openApprove({ ...merchant, id: 'm-2' });
    expect(component.action()).toBe('approve');
  });

  it('clears the secret when leaving the page and never recovers it from the merchant list', () => {
    service.approve.mockReturnValueOnce(of({ merchant: { ...merchant, status: 'APPROVED' },
      clientId: 'client-1', clientSecret: 'one-time-test-secret' }));
    component.openApprove(merchant);
    component.confirmAction();
    fixture.destroy();
    expect(component.handoff()).toBeNull();
    fixture = TestBed.createComponent(MerchantApprovalComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('one-time-test-secret');
  });

  it('does not retain a late approval response after leaving the page', () => {
    const response = new Subject<{ merchant: MerchantSummary; clientId: string; clientSecret: string }>();
    service.approve.mockReturnValueOnce(response);
    component.openApprove(merchant);
    component.confirmAction();
    fixture.destroy();
    response.next({ merchant: { ...merchant, status: 'APPROVED' },
      clientId: 'client-1', clientSecret: 'late-test-secret' });
    response.complete();
    expect(component.handoff()).toBeNull();
  });

  it('allows closing an approval response with no optional secret', () => {
    component.openApprove(merchant);
    component.confirmAction();
    fixture.detectChanges();
    expect(component.handoff()?.clientSecret).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('secret is unavailable');
    (fixture.nativeElement.querySelector('#dismiss-merchant-handoff') as HTMLButtonElement).click();
    expect(component.handoff()).toBeNull();
    component.openApprove({ ...merchant, id: 'm-2' });
    expect(component.action()).toBe('approve');
  });

  it('blocks an empty rejection reason', () => {
    component.openReject(merchant);
    component.confirmAction();
    expect(service.reject).not.toHaveBeenCalled();
    expect(component.reasonError()).toBe('Enter a rejection reason.');
  });

  it('sends a trimmed rejection reason and refreshes after success', () => {
    component.openReject(merchant);
    component.reason.set('  Not verified  ');
    component.confirmAction();
    expect(service.reject).toHaveBeenCalledWith('m-1', { reason: 'Not verified' });
    expect(service.refresh).toHaveBeenCalledTimes(2);
    expect(component.feedback()).toContain('REJECTED');
  });

  it('prevents approval when the merchant is no longer pending', () => {
    component.openApprove({ ...merchant, status: 'APPROVED' });
    expect(component.action()).toBeNull();
    expect(service.approve).not.toHaveBeenCalled();
  });

  it('shows action errors and releases the submit lock', () => {
    service.approve.mockReturnValueOnce(throwError(() => ({ status: 409 })));
    component.openApprove(merchant);
    component.confirmAction();
    expect(component.isSubmitting()).toBe(false);
    expect(component.reasonError()).toBe('Action failed');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Action failed');
    expect(component.handoff()).toBeNull();
  });
});
