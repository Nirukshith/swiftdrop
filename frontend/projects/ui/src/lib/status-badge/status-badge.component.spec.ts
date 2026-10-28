import { TestBed } from '@angular/core/testing';
import { StatusBadgeComponent, humanizeStatus } from './status-badge.component';

describe('StatusBadgeComponent', () => {
  function render(inputs: Record<string, unknown>): HTMLSpanElement {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('span');
  }

  it('humanizes parcel statuses and picks their tone', () => {
    const delivered = render({ status: 'DELIVERED' });
    expect(delivered.textContent?.trim()).toBe('Delivered');
    expect(delivered.className).toContain('bg-success-50');

    const failed = render({ status: 'FAILED' });
    expect(failed.className).toContain('bg-danger-50');

    const outForDelivery = render({ status: 'OUT_FOR_DELIVERY' });
    expect(outForDelivery.textContent?.trim()).toBe('Out for delivery');
    expect(outForDelivery.className).toContain('bg-warning-50');
  });

  it('falls back to neutral for unknown statuses and accepts overrides', () => {
    expect(render({ status: 'PENDING' }).className).toContain('bg-neutral-100');

    const custom = render({ status: 'APPROVED', tone: 'success', label: 'Approved merchant' });
    expect(custom.className).toContain('bg-success-50');
    expect(custom.textContent?.trim()).toBe('Approved merchant');
  });

  it('humanizeStatus converts SNAKE_CASE', () => {
    expect(humanizeStatus('PICKED_UP')).toBe('Picked up');
    expect(humanizeStatus('CREATED')).toBe('Created');
  });
});
