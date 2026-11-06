import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { AdminComponent } from './admin.component';

describe('AdminComponent', () => {
  let component: AdminComponent;
  let fixture: ComponentFixture<AdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates and renders admin shell and navigation', () => {
    expect(component).toBeTruthy();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Admin shell');
    expect(element.textContent).toContain('Merchant approval');
    expect(element.textContent).toContain('Audit log');
    expect(element.querySelector('#admin-staff-management-link')).toBeTruthy();
    expect(element.querySelector('#admin-card-staff-link')).toBeTruthy();
    expect(element.querySelector('#admin-merchant-approval-link')).toBeTruthy();
    expect(element.querySelector('#admin-card-merchant-approval-link')).toBeTruthy();
  });

  it('does not contain the preview role dropdown or embedded dispatcher forms', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).not.toContain('Preview role');
    expect(element.textContent).not.toContain('Add zone');
    expect(element.textContent).not.toContain('Add pricing rule');
  });
});
