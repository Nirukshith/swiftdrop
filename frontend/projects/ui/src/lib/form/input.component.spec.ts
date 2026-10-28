import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { InputComponent } from './input.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent],
  template: `
    <form [formGroup]="form">
      <ui-input label="Email" type="email" formControlName="email" hint="We never share it" />
      <ui-input label="Weight" type="number" formControlName="weight" />
    </form>
  `,
})
class HostComponent {
  form = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email]),
    weight: new FormControl<number | null>(null),
  });
}

describe('InputComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const inputs: HTMLInputElement[] = Array.from(fixture.nativeElement.querySelectorAll('input'));
    return { fixture, form: fixture.componentInstance.form, email: inputs[0], weight: inputs[1] };
  }

  it('links the label, marks required and shows the hint', () => {
    const { fixture, email } = setup();
    const label: HTMLLabelElement = fixture.nativeElement.querySelector('label');
    expect(label.htmlFor).toBe(email.id);
    expect(label.textContent).toContain('*');
    expect(email.getAttribute('aria-required')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('We never share it');
  });

  it('writes typed text to the form control', () => {
    const { form, email } = setup();
    email.value = 'a@b.lk';
    email.dispatchEvent(new Event('input'));
    expect(form.value.email).toBe('a@b.lk');
  });

  it('shows the validation message only after the field is touched', () => {
    const { fixture, email } = setup();
    expect(fixture.nativeElement.textContent).not.toContain('Email is required');

    email.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Email is required');
    expect(email.getAttribute('aria-invalid')).toBe('true');
    expect(email.getAttribute('aria-describedby')).toBe(`${email.id}-error`);
  });

  it('reflects values written by the form', () => {
    const { fixture, form, email } = setup();
    form.patchValue({ email: 'set@code.lk' });
    fixture.detectChanges();
    expect(email.value).toBe('set@code.lk');
  });

  it('emits numbers for type="number" and null when cleared', () => {
    const { form, weight } = setup();
    weight.value = '2.5';
    weight.dispatchEvent(new Event('input'));
    expect(form.value.weight).toBe(2.5);
    weight.value = '';
    weight.dispatchEvent(new Event('input'));
    expect(form.value.weight).toBeNull();
  });

  it('follows the disabled state of the control', () => {
    const { fixture, form, email } = setup();
    form.controls.email.disable();
    fixture.detectChanges();
    expect(email.disabled).toBe(true);
  });
});
