import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CustomerComponent } from './customer.component';
import { CustomerAddress, CustomerParcel, QuoteResponse } from './customer/customer.model';
import { CustomerService } from './customer/customer.service';

describe('CustomerComponent', () => {
  let component: CustomerComponent;
  let fixture: ComponentFixture<CustomerComponent>;
  let customerService: CustomerService;

  const mockQuote: QuoteResponse = {
    currency: 'LKR',
    totalFee: 850.0,
    breakdown: {
      dropoffZoneBaseFee: 350.0,
      weightBandCharge: 250.0,
      sizeCharge: 250.0,
    },
    pickupZone: 'Western',
    dropoffZone: 'Central',
  };

  const mockCreatedParcel: CustomerParcel = {
    id: 'pcl-real-uuid-1234',
    trackingNumber: 'SD-20261105-H7K9N2',
    status: 'CREATED',
    pickupAddress: {
      line1: '142 Havelock Road',
      city: 'Colombo 05',
      district: 'Colombo',
      postalCode: '00500',
    },
    dropoffAddress: {
      line1: '88 Kandy Road',
      city: 'Kadugannawa',
      district: 'Kandy',
      postalCode: '20300',
    },
    weightKg: 2.5,
    size: 'MEDIUM',
    fee: 850.0,
    attemptCount: 0,
    recipientName: 'Kamal Silva',
    recipientPhone: '+94771234567',
    notes: 'Please ring bell',
    createdAt: new Date().toISOString(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerComponent],
      providers: [
        CustomerService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerComponent);
    component = fixture.componentInstance;
    customerService = TestBed.inject(CustomerService);

    const addresses: CustomerAddress[] = [{
      id: 'address-1',
      customerId: 'customer-1',
      label: 'Home',
      line1: '142 Havelock Road',
      line2: null,
      city: 'Colombo 05',
      district: 'Colombo',
      postalCode: '00500',
      isDefault: true,
    }];
    const deliveredParcel = { ...mockCreatedParcel, id: 'delivered-1', status: 'DELIVERED' as const };
    const parcels = [mockCreatedParcel, deliveredParcel];
    jest.spyOn(customerService, 'listAddresses').mockImplementation(() => of([...addresses]));
    jest.spyOn(customerService, 'listParcels').mockImplementation((status) =>
      of(status === 'DELIVERED' ? [deliveredParcel] : [...parcels])
    );
    jest.spyOn(customerService, 'createAddress').mockImplementation((request) => {
      const saved = { ...request, id: 'address-new', customerId: 'customer-1' };
      addresses.push(saved);
      return of(saved);
    });
    jest.spyOn(customerService, 'setDefaultAddress').mockReturnValue(of(addresses[0]));
    jest.spyOn(customerService, 'deleteAddress').mockImplementation((id) => {
      const index = addresses.findIndex((address) => address.id === id);
      addresses.splice(index, 1);
      return of(undefined);
    });
    jest.spyOn(customerService, 'cancelParcel').mockImplementation((id) => {
      const index = parcels.findIndex((parcel) => parcel.id === id);
      const cancelled = { ...parcels[index], status: 'CANCELLED' as const };
      parcels[index] = cancelled;
      return of(cancelled);
    });

    // Spy on getFeeQuote and createParcel by default
    jest.spyOn(customerService, 'getFeeQuote').mockReturnValue(of(mockQuote));
    jest.spyOn(customerService, 'createParcel').mockReturnValue(of(mockCreatedParcel));

    fixture.detectChanges();
  });

  it('should create the customer component', () => {
    expect(component).toBeTruthy();
  });

  it('should load initial parcels and addresses', fakeAsync(() => {
    tick(100);
    expect(component.parcels().length).toBeGreaterThan(0);
    expect(component.addresses().length).toBeGreaterThan(0);
  }));

  it('should switch tabs between deliveries, wizard, and addresses', () => {
    expect(component.activeTab()).toBe('deliveries');

    component.activeTab.set('wizard');
    expect(component.activeTab()).toBe('wizard');

    component.activeTab.set('addresses');
    expect(component.activeTab()).toBe('addresses');
  });

  it('should filter parcels by status', fakeAsync(() => {
    component.selectedStatusFilter.set('DELIVERED');
    component.loadParcels();
    tick(100);

    expect(component.parcels().every((p) => p.status === 'DELIVERED')).toBe(true);
  }));

  it('should navigate through wizard steps with validation and real fee quote integration', fakeAsync(() => {
    component.startNewDelivery();
    expect(component.wizardStep()).toBe(1);
    expect(component.isStep1Valid()).toBe(true);

    component.goToStep(2);
    expect(component.wizardStep()).toBe(2);
    expect(component.isStep2Valid()).toBe(true);

    component.goToStep(3);
    expect(component.wizardStep()).toBe(3);
    tick(50);

    expect(customerService.getFeeQuote).toHaveBeenCalledWith('Colombo', 'Kandy', 2.5, 'MEDIUM');
    expect(component.feePreview()).toBeTruthy();
    expect(component.feePreview()?.totalFee).toBe(850.0);
    expect(component.feePreview()?.breakdown.dropoffZoneBaseFee).toBe(350.0);
    expect(component.feePreview()?.currency).toBe('LKR');

    // Update wizard inputs and verify fee quote re-calculates
    component.wizardData.pickupAddress.district = 'Colombo';
    component.wizardData.dropoffAddress.district = 'Galle';
    component.wizardData.size = 'LARGE';
    component.wizardData.weightKg = 4.0;
    component.updateFeePreview();
    tick(50);

    expect(customerService.getFeeQuote).toHaveBeenCalledWith('Colombo', 'Galle', 4.0, 'LARGE');
  }));

  it('should handle fee quote failure with error state and retry option', fakeAsync(() => {
    jest.spyOn(customerService, 'getFeeQuote').mockReturnValue(
      throwError(() => ({ error: { message: 'Pickup district is not served' }, status: 422 }))
    );

    component.updateFeePreview();
    tick(50);

    expect(component.feePreview()).toBeNull();
    expect(component.isLoadingQuote()).toBe(false);
    expect(component.quoteError()).toContain('Pickup district is not served');

    // Retrying with valid mock quote recovers from error
    jest.spyOn(customerService, 'getFeeQuote').mockReturnValue(of(mockQuote));
    component.updateFeePreview();
    tick(50);

    expect(component.feePreview()).toBeTruthy();
    expect(component.quoteError()).toBeNull();
  }));

  it('should quick-fill address from address book in wizard', fakeAsync(() => {
    tick(100);
    const firstAddr = component.addresses()[0];
    component.selectSavedAddress('pickup', firstAddr.id);

    expect(component.wizardData.pickupAddress.line1).toBe(firstAddr.line1);
    expect(component.wizardData.pickupAddress.district).toBe(firstAddr.district);
  }));

  it('should submit delivery order and receive confirmed tracking number and id', fakeAsync(() => {
    component.startNewDelivery();
    component.goToStep(4);
    tick(50);

    expect(component.orderConfirmed()).toBe(false);
    component.submitDeliveryOrder();
    tick(200);

    expect(customerService.createParcel).toHaveBeenCalledWith(component.wizardData);
    expect(component.orderConfirmed()).toBe(true);
    expect(component.confirmedParcel()).toBeTruthy();
    expect(component.confirmedParcel()?.id).toBe('pcl-real-uuid-1234');
    expect(component.confirmedParcel()?.trackingNumber).toBe('SD-20261105-H7K9N2');
    expect(component.notification()).toContain('booked successfully');
    expect(component.notification()).toContain('SD-20261105-H7K9N2');
  }));

  it('should handle parcel creation failure gracefully', fakeAsync(() => {
    jest.spyOn(customerService, 'createParcel').mockReturnValue(
      throwError(() => ({ error: { message: 'Validation failed on server' }, status: 400 }))
    );

    component.startNewDelivery();
    component.goToStep(4);
    tick(50);

    component.submitDeliveryOrder();
    tick(50);

    expect(component.orderConfirmed()).toBe(false);
    expect(component.isSubmitting()).toBe(false);
    expect(component.notification()).toContain('Validation failed on server');
  }));

  it('should cancel a CREATED parcel', fakeAsync(() => {
    tick(100);
    const createdParcel = component.parcels().find((p) => p.status === 'CREATED');
    if (createdParcel) {
      component.cancelParcel(createdParcel.id);
      tick(100);

      const target = component.parcels().find((p) => p.id === createdParcel.id);
      expect(target?.status).toBe('CANCELLED');
      expect(component.notification()).toContain('cancelled');
    }
  }));

  it('should add a new address to address book', fakeAsync(() => {
    component.openAddressModal();
    expect(component.showAddressModal()).toBe(true);

    component.newAddressForm = {
      label: 'Beach Villa',
      line1: '40 Galle Road',
      line2: '',
      city: 'Bentota',
      district: 'Galle',
      postalCode: '80500',
      isDefault: false,
    };

    expect(component.isAddressFormValid()).toBe(true);
    component.saveNewAddress();
    tick(100);

    expect(component.showAddressModal()).toBe(false);
    expect(component.addresses().some((a) => a.label === 'Beach Villa')).toBe(true);
    expect(component.notification()).toContain('Beach Villa');
  }));

  it('should set an address as default', fakeAsync(() => {
    tick(100);
    component.setDefaultAddress('address-1');
    tick(100);

    expect(component.notification()).toContain('default address');
  }));

  it('should delete an address from address book', fakeAsync(() => {
    tick(100);
    const countBefore = component.addresses().length;
    const target = component.addresses()[0];

    component.deleteAddress(target.id);
    tick(100);

    expect(component.addresses().length).toBe(countBefore - 1);
    expect(component.addresses().some((a) => a.id === target.id)).toBe(false);
  }));
});
