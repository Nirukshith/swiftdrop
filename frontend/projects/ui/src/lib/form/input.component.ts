import { Component, input } from '@angular/core';
import { FormControlBase } from './form-control-base';

/**
 * Text-like input with label, hint and validation message.
 * `<ui-input label="Email" type="email" formControlName="email" />`
 * With type="number" the form value is a number (or null when empty).
 */
@Component({
  selector: 'ui-input',
  standalone: true,
  host: { class: 'block' },
  template: `
    <div class="flex flex-col gap-1">
      @if (label()) {
        <label [for]="inputId()" class="text-sm font-medium text-ink">
          {{ label() }}
          @if (isRequired) {
            <span class="text-danger-600" aria-hidden="true">*</span>
          }
        </label>
      }
      <input
        [id]="inputId()"
        [type]="type()"
        [placeholder]="placeholder()"
        [attr.autocomplete]="autocomplete() || null"
        [value]="value() ?? ''"
        [disabled]="isDisabled()"
        [attr.aria-invalid]="errorMessage ? true : null"
        [attr.aria-describedby]="describedBy"
        [attr.aria-required]="isRequired || null"
        [class]="controlClasses()"
        (input)="onInput($any($event.target).value)"
        (blur)="onTouched()"
      />
      @if (errorMessage; as message) {
        <p [id]="inputId() + '-error'" class="text-xs text-danger-600">{{ message }}</p>
      } @else if (hint()) {
        <p [id]="inputId() + '-hint'" class="text-xs text-ink-muted">{{ hint() }}</p>
      }
    </div>
  `,
})
export class InputComponent extends FormControlBase<string | number> {
  readonly type = input<'text' | 'email' | 'password' | 'number' | 'tel' | 'search' | 'date'>('text');
  readonly placeholder = input('');
  readonly autocomplete = input('');

  protected onInput(raw: string): void {
    if (this.type() === 'number') {
      this.updateValue(raw === '' ? null : Number(raw));
    } else {
      this.updateValue(raw);
    }
  }
}
