import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourierSummaryComponent } from './courier-summary.component';

describe('CourierSummaryComponent', () => {
  let component: CourierSummaryComponent;
  let fixture: ComponentFixture<CourierSummaryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CourierSummaryComponent] }).compileComponents();
    fixture = TestBed.createComponent(CourierSummaryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders counts that reconcile with delivery cards', () => {
    expect(component.summary.totalDeliveries).toBe(component.summary.deliveries.length);
    expect(component.summary.pendingCount + component.summary.completedCount)
      .toBe(component.summary.totalDeliveries);
  });
});
