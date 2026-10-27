import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminComponent } from './admin.component';

describe('AdminComponent', () => {
  let fixture: ComponentFixture<AdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminComponent);
    fixture.detectChanges();
  });

  it('shows dispatcher navigation and mock zones by default', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('Delivery zones');
    expect(element.textContent).toContain('COLOMBO');
    expect(element.textContent).toContain('Pricing rules');
  });

  it('switches to administrator navigation', () => {
    fixture.componentInstance.setRole('ADMIN');
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Merchant approval');
    expect(element.textContent).not.toContain('Add zone');
  });
});
