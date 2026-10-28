import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CardComponent } from './card.component';

@Component({
  standalone: true,
  imports: [CardComponent],
  template: `
    <ui-card title="Recent parcels" subtitle="Last 30 days">
      <button uiCardActions>View all</button>
      <p class="body">Body content</p>
      <span uiCardFooter>Footer text</span>
    </ui-card>
  `,
})
class HostComponent {}

describe('CardComponent', () => {
  it('projects header actions, body and footer', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h2')?.textContent).toContain('Recent parcels');
    expect(el.querySelector('header')?.textContent).toContain('Last 30 days');
    expect(el.querySelector('header button')?.textContent).toContain('View all');
    expect(el.querySelector('.body')).not.toBeNull();
    expect(el.querySelector('footer')?.textContent).toContain('Footer text');
  });

  it('omits the header without a title', () => {
    const fixture = TestBed.createComponent(CardComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('header')).toBeNull();
  });
});
