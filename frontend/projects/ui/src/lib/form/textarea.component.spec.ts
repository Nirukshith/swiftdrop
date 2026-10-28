import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { TextareaComponent } from './textarea.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, TextareaComponent],
  template: `<ui-textarea label="Comment" [rows]="5" [formControl]="comment" [messages]="{ maxlength: 'Keep it under 10 characters' }" />`,
})
class HostComponent {
  comment = new FormControl('', Validators.maxLength(10));
}

describe('TextareaComponent', () => {
  it('writes text to the control and uses message overrides', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea');
    expect(textarea.rows).toBe(5);

    textarea.value = 'this is far too long';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.componentInstance.comment.value).toBe('this is far too long');
    expect(fixture.nativeElement.textContent).toContain('Keep it under 10 characters');
  });
});
