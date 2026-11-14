import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="yum-page">
      <!-- Top Brand Bar -->
      <header class="top-bar">
        <div class="brand">
          <svg class="brand-icon" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="10" fill="#ffffff" />
            <path d="M8 12L16 7L24 12V22L16 27L8 22V12Z" fill="#a53a26" />
            <path d="M16 7V27M8 12L24 22M24 12L8 22" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round" />
          </svg>
          <span class="brand-text">SwiftDrop</span>
        </div>

        <nav class="nav-links">
          <a routerLink="/register" class="nav-link">Register</a>
          <a routerLink="/login" class="nav-pill">Sign In</a>
        </nav>
      </header>

      <main class="landing-content">
        <!-- Hero Section -->
        <section class="hero-section">
          <div class="hero-badge">
            <span class="badge-dot"></span> Next-Generation Parcel Delivery
          </div>

          <h1 class="hero-headline">The parcel delivery you can talk to</h1>

          <p class="hero-lede">
            Say “two packages to Colombo by 2 PM”, and dispatch is scheduled.
            SwiftDrop calculates dynamic rates, coordinates couriers live, and tracks every stop across Sri Lanka.
          </p>

          <!-- Action Buttons -->
          <div class="hero-cta-group">
            <a routerLink="/register" class="btn-dark">
              <span>Get Started Free</span>
              <svg class="btn-arrow" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M10.293 3.293a1 1 0 011.414 0l6 6a1 1 0 010 1.414l-6 6a1 1 0 01-1.414-1.414L14.586 11H3a1 1 0 110-2h11.586l-4.293-4.293a1 1 0 010-1.414z" clip-rule="evenodd"/>
              </svg>
            </a>
            <a routerLink="/track" class="btn-white">
              <svg class="icon-small" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <span>Track a Parcel</span>
            </a>
            <a routerLink="/login" class="btn-glass">
              <span>Sign In</span>
            </a>
          </div>

          <!-- Spoken Chips (YumLog hallmark) -->
          <ul class="said-chips" aria-label="Spoken delivery commands">
            <li class="said-chip chip-1">
              <svg class="chip-icon mic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="22"/>
              </svg>
              <span>“Deliver package #SD-8942 by 2 PM”</span>
            </li>
            <li class="said-chip chip-2">
              <svg class="chip-icon parcel" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                <line x1="12" y1="22.08" x2="12" y2="12"/>
              </svg>
              <span>“Courier arriving in 8 minutes”</span>
            </li>
            <li class="said-chip chip-3">
              <svg class="chip-icon store" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
              <span>“Bulk dispatch 50 orders today”</span>
            </li>
          </ul>
        </section>

        <!-- Feature Bento Grid (Tactile YumLog Cards) -->
        <section class="bento-section">
          <div class="bento-grid">
            <div class="bento-card">
              <div class="card-icon-badge">🚀</div>
              <h3>Live GPS &amp; Timeline Tracking</h3>
              <p>Watch couriers approach in real time via live Server-Sent Events, instant milestone alerts, and verified proof photos.</p>
              <a routerLink="/track" class="card-link">Try live tracking →</a>
            </div>

            <div class="bento-card">
              <div class="card-icon-badge">⚡</div>
              <h3>Dynamic Pricing Engine</h3>
              <p>Transparent rates computed by distance, parcel weight, delivery zones, and urgent handling with zero hidden charges.</p>
              <a routerLink="/register" class="card-link">Calculate delivery fee →</a>
            </div>

            <div class="bento-card">
              <div class="card-icon-badge">🏪</div>
              <h3>Merchant Bulk Logistics</h3>
              <p>High-volume CSV order booking, OAuth API credentials, and webhooks designed for e-commerce scale.</p>
              <a routerLink="/merchant-registration" class="card-link">Join partner network →</a>
            </div>

            <div class="bento-card">
              <div class="card-icon-badge">🤖</div>
              <h3>AI Delivery Assistant</h3>
              <p>Natural language delivery queries, automated schedule lookups, and instant courier dispatch assistance.</p>
              <a routerLink="/register" class="card-link">Explore AI chat →</a>
            </div>
          </div>
        </section>

        <!-- Bottom Footer Callout -->
        <footer class="landing-footer">
          <div class="footer-inner">
            <span class="footer-brand">SwiftDrop</span>
            <p>Next-generation distributed parcel delivery platform for Sri Lanka.</p>
            <div class="footer-links">
              <a routerLink="/login">Sign In</a>
              <a routerLink="/register">Register</a>
              <a routerLink="/merchant-registration">Merchants</a>
              <a routerLink="/track">Track</a>
            </div>
          </div>
        </footer>
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      --disp: ui-rounded, 'SF Pro Rounded', 'Nunito', system-ui, -apple-system, sans-serif;
      --terracotta: #a53a26;
      --coral: #f2533a;
      --ink: #1d1b2c;
      --ink-3: #6f6d85;
      font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', system-ui, 'Segoe UI', Roboto, sans-serif;
    }
    .yum-page {
      position: relative;
      min-height: 100vh;
      margin: 0;
      padding: 2.25rem 2rem 3rem;
      background: radial-gradient(55% 65% at 75% 45%, #b8432f 0%, #98311e 65%, #882b1a 100%), #98311e;
      color: #fff;
      overflow-x: hidden;
    }
    .top-bar {
      max-width: 1200px;
      margin: 0 auto 3.5rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 0.75rem;
      color: #fff;
      text-decoration: none;
    }
    .brand-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    }
    .brand-text {
      font-family: var(--disp);
      font-size: 1.55rem;
      font-weight: 900;
      letter-spacing: -0.03em;
    }
    .nav-links {
      display: flex;
      align-items: center;
      gap: 1.5rem;
    }
    .nav-link {
      color: rgba(255,255,255,0.85);
      text-decoration: none;
      font-size: 0.95rem;
      font-weight: 600;
      transition: color 0.2s;
    }
    .nav-link:hover { color: #fff; }
    .nav-pill {
      display: inline-flex;
      align-items: center;
      height: 40px;
      padding: 0 1.35rem;
      border-radius: 9999px;
      background: var(--ink);
      color: #fff;
      font-size: 0.875rem;
      font-weight: 700;
      text-decoration: none;
      transition: background-color 0.2s, transform 0.2s;
    }
    .nav-pill:hover, .btn-dark:hover {
      background: #000;
      transform: translateY(-1px);
    }
    .landing-content {
      max-width: 1200px;
      margin: 0 auto;
    }
    .hero-section {
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 1.5rem 0 4rem;
    }
    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4rem 1rem;
      border-radius: 9999px;
      background: rgba(255,255,255,0.15);
      backdrop-filter: blur(8px);
      font-size: 0.85rem;
      font-weight: 700;
      color: #fff;
      margin-bottom: 1.75rem;
    }
    .badge-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #4ade80;
      box-shadow: 0 0 8px #4ade80;
    }
    .hero-headline {
      font-family: var(--disp);
      font-size: clamp(2.75rem, 5.5vw, 5rem);
      font-weight: 900;
      line-height: 0.94;
      letter-spacing: -0.035em;
      color: #fff;
      margin: 0 0 1.75rem;
      max-width: 22ch;
      text-shadow: 0 2px 0 rgba(140,30,15,0.25);
    }
    .hero-lede {
      max-width: 36rem;
      font-size: 1.2rem;
      line-height: 1.55;
      color: rgba(255,255,255,0.9);
      margin: 0 0 2.5rem;
    }
    .hero-cta-group {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      align-items: center;
      gap: 1rem;
      margin-bottom: 3.5rem;
    }
    .btn-dark {
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      height: 54px;
      padding: 0 1.75rem;
      border-radius: 9999px;
      background: var(--ink);
      color: #fff;
      font-size: 1.05rem;
      font-weight: 800;
      text-decoration: none;
      box-shadow: 0 14px 30px -10px rgba(29,27,44,0.5);
      transition: all 0.2s ease;
    }
    .btn-arrow { width: 18px; height: 18px; }
    .btn-white {
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      height: 54px;
      padding: 0 1.75rem;
      border-radius: 9999px;
      background: #fff;
      color: var(--ink);
      font-size: 1.05rem;
      font-weight: 800;
      text-decoration: none;
      box-shadow: 0 14px 30px -10px rgba(90,15,5,0.4);
      transition: all 0.2s ease;
    }
    .btn-white:hover {
      background: #f8fafc;
      transform: translateY(-1px);
    }
    .icon-small { width: 18px; height: 18px; color: var(--coral); }
    .btn-glass {
      display: inline-flex;
      align-items: center;
      height: 54px;
      padding: 0 1.5rem;
      border-radius: 9999px;
      background: rgba(255,255,255,0.15);
      color: #fff;
      font-size: 1.05rem;
      font-weight: 700;
      text-decoration: none;
      backdrop-filter: blur(8px);
      border: 1px solid rgba(255,255,255,0.2);
      transition: all 0.2s ease;
    }
    .btn-glass:hover {
      background: rgba(255,255,255,0.25);
      transform: translateY(-1px);
    }
    .said-chips {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 1rem;
      max-width: 48rem;
    }
    .said-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem 1.25rem;
      border-radius: 9999px;
      background: #fff;
      color: var(--ink);
      font-size: 0.95rem;
      font-weight: 700;
      box-shadow: 0 14px 30px -12px rgba(90,15,5,0.45);
      animation: bob 7s ease-in-out infinite;
    }
    .chip-2 { animation-delay: -2.3s; }
    .chip-3 { animation-delay: -4.6s; }
    @keyframes bob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
    .chip-icon { width: 18px; height: 18px; color: var(--coral); flex-shrink: 0; }
    .bento-section {
      padding: 2rem 0 4rem;
    }
    .bento-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 1.5rem;
    }
    .bento-card {
      background: #fff;
      color: var(--ink);
      border-radius: 28px;
      padding: 2.25rem 1.75rem;
      box-shadow: 0 20px 45px -15px rgba(29,27,44,0.3);
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    .bento-card:hover {
      transform: translateY(-3px);
      box-shadow: 0 24px 55px -12px rgba(29,27,44,0.4);
    }
    .card-icon-badge {
      font-size: 2rem;
      margin-bottom: 1rem;
    }
    .bento-card h3 {
      font-family: var(--disp);
      font-size: 1.35rem;
      font-weight: 900;
      letter-spacing: -0.02em;
      margin: 0 0 0.65rem;
      color: var(--ink);
    }
    .bento-card p {
      font-size: 0.95rem;
      color: var(--ink-3);
      line-height: 1.5;
      margin: 0 0 1.25rem;
      flex-grow: 1;
    }
    .card-link {
      font-size: 0.875rem;
      font-weight: 800;
      color: var(--terracotta);
      text-decoration: none;
    }
    .card-link:hover { text-decoration: underline; }
    .landing-footer {
      border-top: 1px solid rgba(255,255,255,0.15);
      padding: 2.5rem 0 1.5rem;
      margin-top: 2rem;
    }
    .footer-inner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      font-size: 0.9rem;
      color: rgba(255,255,255,0.8);
    }
    .footer-brand {
      font-family: var(--disp);
      font-weight: 900;
      font-size: 1.2rem;
      color: #fff;
    }
    .footer-links {
      display: flex;
      gap: 1.5rem;
    }
    .footer-links a {
      color: rgba(255,255,255,0.85);
      text-decoration: none;
      font-weight: 600;
    }
    .footer-links a:hover { color: #fff; }
    @media (max-width: 768px) {
      .top-bar { flex-direction: column; gap: 1rem; margin-bottom: 2rem; }
      .hero-headline { font-size: 2.5rem; }
      .hero-cta-group { flex-direction: column; width: 100%; }
      .btn-dark, .btn-white, .btn-glass { width: 100%; justify-content: center; }
      .footer-inner { flex-direction: column; text-align: center; }
    }
  `]
})
export class LandingComponent {}
