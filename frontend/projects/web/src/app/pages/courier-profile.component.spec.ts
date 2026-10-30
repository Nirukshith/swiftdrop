import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourierProfileComponent } from './courier-profile.component';

describe('CourierProfileComponent', () => {
  let component: CourierProfileComponent;
  let fixture: ComponentFixture<CourierProfileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CourierProfileComponent] }).compileComponents();
    fixture = TestBed.createComponent(CourierProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the courier profile and toggles availability', () => {
    const initial = component.service.profile().availability;
    component.service.toggleAvailability();
    expect(component.service.profile().availability).toBe(!initial);
  });
});
