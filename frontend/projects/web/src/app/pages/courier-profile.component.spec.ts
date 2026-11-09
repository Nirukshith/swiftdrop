import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourierProfileComponent } from './courier-profile.component';

describe('CourierProfileComponent', () => {
  let component: CourierProfileComponent;
  let fixture: ComponentFixture<CourierProfileComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CourierProfileComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(CourierProfileComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders the authenticated profile after loading', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/courier/me').flush({ userId: 'courier-1', name: 'Kamal Perera', phone: '+94771234567', zone: 'Colombo', availability: true, vehicle: 'MOTORBIKE' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Kamal Perera');
    expect(fixture.nativeElement.textContent).toContain('Colombo');
    expect(fixture.nativeElement.textContent).toContain('Available');
  });

  it('shows a profile loading failure', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/courier/me').flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('session has expired');
  });

  it('updates availability only after the server confirms it', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/courier/me').flush({ userId: 'courier-1', name: 'Kamal', phone: null, zone: null, availability: true, vehicle: null });
    component.toggleAvailability();
    const request = http.expectOne('/api/v1/courier/availability');
    expect(component.profile()?.availability).toBe(true);
    request.flush({ userId: 'courier-1', name: 'Kamal', phone: null, zone: null, availability: false, vehicle: null });
    expect(component.profile()?.availability).toBe(false);
  });
});
