import { TestBed } from '@angular/core/testing';
import { PaginationComponent } from './pagination.component';

describe('PaginationComponent', () => {
  function setup(page: number, size: number, totalElements: number) {
    const fixture = TestBed.createComponent(PaginationComponent);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('size', size);
    fixture.componentRef.setInput('totalElements', totalElements);
    fixture.detectChanges();
    const emitted: number[] = [];
    fixture.componentInstance.pageChange.subscribe((p) => emitted.push(p));
    const buttons = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('button'));
    return { fixture, emitted, buttons, el: fixture.nativeElement as HTMLElement };
  }

  it('shows the visible range for 0-based pages', () => {
    const { el } = setup(1, 20, 42);
    expect(el.textContent?.replace(/\s+/g, ' ')).toContain('Showing 21–40 of 42');
  });

  it('marks the current page and emits 0-based indexes', () => {
    const { buttons, emitted } = setup(0, 10, 35);
    const current = buttons().find((b) => b.getAttribute('aria-current') === 'page');
    expect(current?.textContent?.trim()).toBe('1');

    buttons().find((b) => b.textContent?.trim() === '3')?.click();
    buttons().find((b) => b.getAttribute('aria-label') === 'Next page')?.click();
    expect(emitted).toEqual([2, 1]);
  });

  it('disables previous on the first page and next on the last page', () => {
    const first = setup(0, 10, 25);
    expect(first.buttons().find((b) => b.getAttribute('aria-label') === 'Previous page')?.disabled).toBe(true);

    const last = setup(2, 10, 25);
    expect(last.buttons().find((b) => b.getAttribute('aria-label') === 'Next page')?.disabled).toBe(true);
  });

  it('limits the page numbers to the window around the current page', () => {
    const { buttons } = setup(9, 10, 200);
    const numbers = buttons()
      .map((b) => b.textContent?.trim())
      .filter((t) => /^\d+$/.test(t ?? ''));
    expect(numbers).toEqual(['8', '9', '10', '11', '12']);
  });

  it('hides the buttons for a single page and handles no results', () => {
    const single = setup(0, 20, 5);
    expect(single.buttons().length).toBe(0);
    expect(setup(0, 20, 0).el.textContent).toContain('No results');
  });
});
