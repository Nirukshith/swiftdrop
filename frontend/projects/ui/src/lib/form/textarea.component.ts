import { Component, input } from '@angular/core';
import { FormControlBase } from './form-control-base';

/** Multi-line text input with label, hint and validation message. */
@Component({
  selector: 'ui-textarea',
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
      <textarea
        [id]="inputId()"
        [rows]="rows()"
        [placeholder]="placeholder()"
        [value]="value() ?? ''"
        [disabled]="isDisabled()"
        [attr.aria-invalid]="errorMessage ? true : null"
        [attr.aria-describedby]="describedBy"
        [attr.aria-required]="isRequired || null"
        [class]="controlClasses()"
        (input)="updateValue($any($event.target).value)"
        (blur)="onTouched()"
      ></textarea>
      @if (errorMessage; as message) {
        <p [id]="inputId() + '-error'" class="text-xs text-danger-600">{{ message }}</p>
      } @else if (hint()) {
        <p [id]="inputId() + '-hint'" class="text-xs text-ink-muted">{{ hint() }}</p>
      }
    </div>
  `,
})
export class TextareaComponent extends FormControlBase<string> {
  readonly rows = input(3);
  readonly placeholder = input('');
}
