import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LandingComponent } from './landing.component';

describe('LandingComponent', () => {
  let fixture: ComponentFixture<LandingComponent>;
  let component: LandingComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(LandingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the landing component', () => {
    expect(component).toBeTruthy();
  });

  it('renders brand wordmark and headline', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-text')?.textContent).toContain('SwiftDrop');
    expect(compiled.querySelector('h1')?.textContent).toContain('The parcel delivery you can talk to');
  });

  it('renders call-to-action buttons for registration, tracking, and login', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const registerLinks = compiled.querySelectorAll('a[routerLink="/register"]');
    expect(registerLinks.length).toBeGreaterThan(0);

    const trackLinks = compiled.querySelectorAll('a[routerLink="/track"]');
    expect(trackLinks.length).toBeGreaterThan(0);

    const loginLinks = compiled.querySelectorAll('a[routerLink="/login"]');
    expect(loginLinks.length).toBeGreaterThan(0);
  });

  it('renders Customer, Merchant, and Sign In navigation links in header', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const navLinks = compiled.querySelectorAll('.nav-links a');
    expect(navLinks.length).toBe(3);
    expect(navLinks[0].textContent.trim()).toBe('Customer');
    expect(navLinks[1].textContent.trim()).toBe('Merchant');
    expect(navLinks[2].textContent.trim()).toBe('Sign In');
  });

  it('renders spoken chips and feature bento cards', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const chips = compiled.querySelectorAll('.said-chip');
    expect(chips.length).toBe(3);

    const bentoCards = compiled.querySelectorAll('.bento-card');
    expect(bentoCards.length).toBe(4);
  });
});

