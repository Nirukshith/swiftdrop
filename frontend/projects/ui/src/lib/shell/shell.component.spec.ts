import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ShellComponent, ShellNavItem } from './shell.component';

@Component({ standalone: true, template: '' })
class BlankComponent {}

@Component({
  standalone: true,
  imports: [ShellComponent],
  template: `
    <ui-shell appName="SwiftDrop" pageTitle="My parcels" [navItems]="nav">
      <button uiShellActions class="logout">Log out</button>
      <p class="page">Page content</p>
    </ui-shell>
  `,
})
class HostComponent {
  nav: ShellNavItem[] = [
    { label: 'Dashboard', link: '/customer/dashboard' },
    { label: 'My parcels', link: '/customer/parcels' },
  ];
}

describe('ShellComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', component: BlankComponent }])],
    });
  });

  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('renders brand, title, actions, navigation and content', () => {
    const { el } = setup();
    expect(el.querySelector('header a')?.textContent).toContain('SwiftDrop');
    expect(el.querySelector('h1')?.textContent).toContain('My parcels');
    expect(el.querySelector('.logout')).not.toBeNull();
    expect(el.querySelector('main .page')).not.toBeNull();
    const links = Array.from(el.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/customer/dashboard', '/customer/parcels']);
  });

  it('toggles the mobile navigation', () => {
    const { fixture, el } = setup();
    const toggle = el.querySelector('[aria-label="Toggle navigation"]') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(el.querySelector('#ui-shell-mobile-nav')).toBeNull();

    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelectorAll('#ui-shell-mobile-nav a').length).toBe(2);
  });
});
