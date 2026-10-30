import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DialogComponent } from './dialog.component';

@Component({
  standalone: true,
  imports: [DialogComponent],
  template: `
    <ui-dialog [(open)]="open" title="Cancel parcel?" [dismissible]="dismissible()" (closed)="closedCount = closedCount + 1">
      <p>This cannot be undone.</p>
      <button uiDialogFooter class="confirm">Confirm</button>
    </ui-dialog>
  `,
})
class HostComponent {
  open = signal(false);
  dismissible = signal(true);
  closedCount = 0;
}

describe('DialogComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const dialog = (): HTMLElement | null => fixture.nativeElement.querySelector('[role="dialog"]');
    const openDialog = () => {
      fixture.componentInstance.open.set(true);
      fixture.detectChanges();
    };
    return { fixture, host: fixture.componentInstance, dialog, openDialog };
  }

  it('renders nothing while closed', () => {
    const { dialog } = setup();
    expect(dialog()).toBeNull();
  });

  it('renders an accessible modal with content and footer when open', () => {
    const { dialog, openDialog } = setup();
    openDialog();
    const el = dialog()!;
    expect(el.getAttribute('aria-modal')).toBe('true');
    expect(document.getElementById(el.getAttribute('aria-labelledby')!)?.textContent).toBe('Cancel parcel?');
    expect(el.textContent).toContain('This cannot be undone.');
    expect(el.querySelector('footer .confirm')).not.toBeNull();
  });

  it('closes on the close button, updates [(open)] and emits closed', () => {
    const { fixture, host, dialog, openDialog } = setup();
    openDialog();
    (dialog()!.querySelector('[aria-label="Close"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.open()).toBe(false);
    expect(host.closedCount).toBe(1);
    expect(dialog()).toBeNull();
  });

  it('closes on Escape and on the backdrop', () => {
    const { fixture, host, openDialog } = setup();
    openDialog();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(host.open()).toBe(false);

    openDialog();
    (fixture.nativeElement.querySelector('[data-testid="ui-dialog-backdrop"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(host.open()).toBe(false);
    expect(host.closedCount).toBe(2);
  });

  it('ignores Escape and backdrop when not dismissible', () => {
    const { fixture, host, openDialog } = setup();
    host.dismissible.set(false);
    openDialog();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    (fixture.nativeElement.querySelector('[data-testid="ui-dialog-backdrop"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(host.open()).toBe(true);
  });
});
