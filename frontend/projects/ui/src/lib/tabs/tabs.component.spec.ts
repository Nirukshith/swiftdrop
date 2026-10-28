import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TabComponent, TabsComponent } from './tabs.component';

@Component({
  standalone: true,
  imports: [TabsComponent, TabComponent],
  template: `
    <ui-tabs [(selectedIndex)]="selected" ariaLabel="Parcel filters">
      <ui-tab label="Active"><p class="p-active">Active parcels</p></ui-tab>
      <ui-tab label="Delivered"><p class="p-delivered">Delivered parcels</p></ui-tab>
      <ui-tab label="Cancelled"><p>Cancelled parcels</p></ui-tab>
    </ui-tabs>
  `,
})
class HostComponent {
  selected = 0;
}

describe('TabsComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const tabs = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('[role="tab"]'));
    const panels = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('ui-tab'));
    return { fixture, host: fixture.componentInstance, tabs, panels };
  }

  it('renders an accessible tab list with only the first panel visible', () => {
    const { tabs, panels } = setup();
    expect(tabs().map((t) => t.textContent?.trim())).toEqual(['Active', 'Delivered', 'Cancelled']);
    expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs()[0].getAttribute('aria-controls')).toBe(panels()[0].id);
    expect(panels()[0].getAttribute('aria-labelledby')).toBe(tabs()[0].id);
    expect(panels().map((p) => p.hidden)).toEqual([false, true, true]);
  });

  it('switches panels on click and updates the two-way binding', () => {
    const { fixture, host, tabs, panels } = setup();
    tabs()[1].click();
    fixture.detectChanges();
    expect(host.selected).toBe(1);
    expect(panels().map((p) => p.hidden)).toEqual([true, false, true]);
    expect(tabs()[1].getAttribute('tabindex')).toBe('0');
    expect(tabs()[0].getAttribute('tabindex')).toBe('-1');
  });

  it('moves with arrow keys and wraps around', () => {
    const { fixture, host, tabs } = setup();
    tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();
    expect(host.selected).toBe(2);
    tabs()[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(host.selected).toBe(0);
  });
});
