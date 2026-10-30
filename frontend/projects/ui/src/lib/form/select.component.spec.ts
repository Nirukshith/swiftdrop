import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { SelectComponent, SelectOption } from './select.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, SelectComponent],
  template: `<ui-select label="Size" placeholder="Choose a size" [options]="options" [formControl]="size" />`,
})
class HostComponent {
  options: SelectOption<number>[] = [
    { value: 1, label: 'Small' },
    { value: 2, label: 'Medium' },
  ];
  size = new FormControl<number | null>(null, Validators.required);
}

describe('SelectComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    return { fixture, host: fixture.componentInstance, select };
  }

  it('renders the placeholder and options', () => {
    const { select } = setup();
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual(['Choose a size', 'Small', 'Medium']);
  });

  it('keeps the option value type when selecting', () => {
    const { host, select } = setup();
    select.selectedIndex = 2;
    select.dispatchEvent(new Event('change'));
    expect(host.size.value).toBe(2);
  });

  it('maps the placeholder back to null', () => {
    const { host, select } = setup();
    select.selectedIndex = 1;
    select.dispatchEvent(new Event('change'));
    select.selectedIndex = 0;
    select.dispatchEvent(new Event('change'));
    expect(host.size.value).toBeNull();
  });

  it('selects the option written by the form', () => {
    const { fixture, host, select } = setup();
    host.size.setValue(1);
    fixture.detectChanges();
    expect(select.options[1].selected).toBe(true);
  });

  it('shows the required message after blur', () => {
    const { fixture, select } = setup();
    select.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Size is required');
  });
});
