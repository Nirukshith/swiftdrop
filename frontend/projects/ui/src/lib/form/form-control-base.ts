import { Directive, inject, input, signal } from '@angular/core';
import { ControlValueAccessor, NgControl, Validators } from '@angular/forms';
import { validationMessage, ValidationMessageOverrides } from './validation-messages';

let nextId = 0;

/**
 * Shared behaviour of ui-input, ui-select and ui-textarea: ControlValueAccessor plumbing,
 * label/hint/id inputs, and the validation message of the bound form control.
 * The control registers itself on NgControl, so it works with formControlName,
 * [formControl] and ngModel without extra providers.
 */
@Directive()
export abstract class FormControlBase<T> implements ControlValueAccessor {
  readonly label = input('');
  readonly hint = input('');
  readonly inputId = input(`ui-field-${nextId++}`);
  /** Per-error message overrides, see validationMessage(). */
  readonly messages = input<ValidationMessageOverrides>({});

  protected readonly ngControl = inject(NgControl, { self: true, optional: true });
  protected readonly value = signal<T | null>(null);
  protected readonly isDisabled = signal(false);

  private onChange: (value: T | null) => void = () => {};
  protected onTouched: () => void = () => {};

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
  }

  writeValue(value: T | null): void {
    this.value.set(value ?? null);
  }

  registerOnChange(fn: (value: T | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.isDisabled.set(isDisabled);
  }

  protected updateValue(value: T | null): void {
    this.value.set(value);
    this.onChange(value);
  }

  /** Message to show, only after the user touched or changed the field. */
  protected get errorMessage(): string | null {
    const control = this.ngControl?.control;
    if (!control || control.valid || !(control.touched || control.dirty)) {
      return null;
    }
    return validationMessage(control.errors, this.label() || 'This field', this.messages());
  }

  protected get isRequired(): boolean {
    return this.ngControl?.control?.hasValidator(Validators.required) ?? false;
  }

  protected get describedBy(): string | null {
    if (this.errorMessage) {
      return `${this.inputId()}-error`;
    }
    return this.hint() ? `${this.inputId()}-hint` : null;
  }

  protected controlClasses(): string {
    return (
      'block w-full rounded-control border bg-surface px-3 py-2 text-sm text-ink shadow-sm ' +
      'placeholder:text-ink-subtle focus:outline-none focus:ring-2 ' +
      'disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-ink-subtle ' +
      (this.errorMessage
        ? 'border-danger-500 focus:border-danger-500 focus:ring-danger-200'
        : 'border-surface-border focus:border-brand-500 focus:ring-brand-200')
    );
  }
}
