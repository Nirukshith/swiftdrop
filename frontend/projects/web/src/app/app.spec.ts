import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Hello, web');
  });

  it('should not render notification bell for signed-out visitors', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-notification-bell')).toBeFalsy();
  });

  it('should render notification bell for customers only', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    jest.spyOn(app, 'isAuthPage').mockReturnValue(false);
    jest.spyOn(app.authService, 'hasRole').mockImplementation((role: string) => role === 'CUSTOMER');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-notification-bell')).toBeTruthy();
  });

  it('should not render notification bell for non-customer roles', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    jest.spyOn(app.authService, 'hasRole').mockImplementation((role: string) => role === 'DISPATCHER');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-notification-bell')).toBeFalsy();
  });

  it('should not show admin or dispatcher links to signed-out visitor', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Staff Management');
    expect(compiled.textContent).not.toContain('Dispatch');
    expect(compiled.textContent).not.toContain('Zones');
    expect(compiled.textContent).not.toContain('Pricing');
  });

  it('should show admin links only when user has ADMIN role', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    jest.spyOn(app, 'isAuthPage').mockReturnValue(false);
    jest.spyOn(app.authService, 'hasRole').mockImplementation((role: string) => role === 'ADMIN');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Admin');
    expect(compiled.textContent).toContain('Staff Management');
    expect(compiled.textContent).not.toContain('Dispatch');
    expect(compiled.textContent).not.toContain('Zones');
    expect(compiled.textContent).not.toContain('Pricing');
  });

  it('should show dispatcher links only when user has DISPATCHER role', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    jest.spyOn(app, 'isAuthPage').mockReturnValue(false);
    jest.spyOn(app.authService, 'hasRole').mockImplementation((role: string) => role === 'DISPATCHER');
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Dispatch');
    expect(compiled.textContent).toContain('Zones');
    expect(compiled.textContent).toContain('Pricing');
    expect(compiled.textContent).not.toContain('Staff Management');
  });

  it('should hide top bar navigation on auth routes (login, register, merchant-registration)', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    jest.spyOn(app, 'isAuthPage').mockReturnValue(true);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('header')).toBeFalsy();
  });

  it('should show top bar navigation on non-auth signed-in routes', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    jest.spyOn(app, 'isAuthPage').mockReturnValue(false);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('header')).toBeTruthy();
  });
});
