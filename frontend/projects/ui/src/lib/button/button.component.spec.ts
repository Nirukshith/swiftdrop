import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ButtonComponent } from './button.component';

@Component({
  standalone: true,
  imports: [ButtonComponent],
  template: `<ui-button [variant]="variant()" [loading]="loading()" [disabled]="disabled()" (click)="clicks = clicks + 1">Save</ui-button>`,
})
class HostComponent {
  variant = signal<'primary' | 'danger'>('primary');
  loading = signal(false);
  disabled = signal(false);
  clicks = 0;
}

describe('ButtonComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const button = (): HTMLButtonElement => fixture.nativeElement.querySelector('button');
    return { fixture, host: fixture.componentInstance, button };
  }

  it('renders projected text with the primary style by default', () => {
    const { button } = setup();
    expect(button().textContent?.trim()).toBe('Save');
    expect(button().className).toContain('bg-brand-600');
    expect(button().type).toBe('button');
  });

  it('applies the danger variant', () => {
    const { fixture, host, button } = setup();
    host.variant.set('danger');
    fixture.detectChanges();
    expect(button().className).toContain('bg-danger-600');
  });

  it('emits clicks when enabled', () => {
    const { host, button } = setup();
    button().click();
    expect(host.clicks).toBe(1);
  });

  it('disables the button and shows a spinner while loading', () => {
    const { fixture, host, button } = setup();
    host.loading.set(true);
    fixture.detectChanges();
    expect(button().disabled).toBe(true);
    expect(button().getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('ui-spinner')).not.toBeNull();
    button().click();
    expect(host.clicks).toBe(0);
  });

  it('does not emit clicks when disabled', () => {
    const { fixture, host, button } = setup();
    host.disabled.set(true);
    fixture.detectChanges();
    button().click();
    expect(host.clicks).toBe(0);
  });
});
