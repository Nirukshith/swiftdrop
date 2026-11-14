import {
  Component,
  OnInit,
  AfterViewChecked,
  ViewChild,
  ElementRef,
  signal,
  computed,
  inject,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { AuthService } from 'shared-auth';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  timestamp: Date;
  error?: boolean;
}

interface ChatMessageResponse {
  reply: string;
  conversationId: string;
}

const MAX_MESSAGE_LENGTH = 500;

const SUGGESTIONS = [
  'Where is my parcel SD-20261114-BEK6JR?',
  'How much to send 2 kg from Colombo to Kandy?',
  'What is the delivery status of my latest parcel?',
];

/**
 * Customer AI Delivery Assistant Chat Component (M3).
 * Styled with the Nomu Customer Portal full-screen layout and color palette.
 * Calls POST /api/v1/assistant/chat with the customer's JWT (attached by AuthInterceptor).
 */
@Component({
  selector: 'app-assistant-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, RouterLink],
  template: `
    <div class="min-h-screen bg-[#faf8f6] flex flex-col md:flex-row antialiased font-sans text-[#1d1b2c]">
      <!-- Left Icon Navigation Rail (Customer Portal Theme) -->
      <aside
        class="w-full md:w-20 bg-[#faf8f6] border-b md:border-b-0 md:border-r border-[#eee7e0] flex md:flex-col items-center justify-between p-4 md:py-6 flex-shrink-0 z-20"
      >
        <!-- Logo Hexagon -->
        <a
          routerLink="/customer"
          class="w-12 h-12 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md hover:scale-105 transition-transform"
          title="SwiftDrop Portal"
        >
          <svg class="w-7 h-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
          </svg>
        </a>

        <!-- Main Navigation Icons -->
        <nav class="flex md:flex-col items-center gap-3">
          <!-- Deliveries List -->
          <a
            routerLink="/customer"
            class="w-11 h-11 rounded-2xl text-[#6f6d85] hover:bg-[#eee7e0] hover:text-[#1d1b2c] flex items-center justify-center transition-colors"
            title="Deliveries"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
            </svg>
          </a>

          <!-- AI Assistant (Active) -->
          <a
            routerLink="/customer/assistant"
            class="w-11 h-11 rounded-2xl bg-[#ffffff] text-[#a53a26] shadow-sm flex items-center justify-center transition-colors"
            title="AI Assistant"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
            </svg>
          </a>

          <!-- Public Live Tracking -->
          <a
            routerLink="/track"
            class="w-11 h-11 rounded-2xl text-[#6f6d85] hover:bg-[#eee7e0] hover:text-[#1d1b2c] flex items-center justify-center transition-colors"
            title="Live Tracking"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </a>
        </nav>

        <!-- Bottom Actions (New Delivery + Logout) -->
        <div class="flex md:flex-col items-center gap-3">
          <a
            routerLink="/customer"
            class="w-11 h-11 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md hover:bg-[#8c2e1d] transition-colors"
            title="New Delivery"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
            </svg>
          </a>
          <button
            type="button"
            (click)="logout()"
            class="w-11 h-11 rounded-2xl text-[#6f6d85] hover:bg-[#eee7e0] hover:text-[#1d1b2c] flex items-center justify-center transition-colors cursor-pointer"
            title="Sign Out"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
          </button>
        </div>
      </aside>

      <!-- Main Content Column -->
      <main class="flex-1 flex flex-col min-w-0 bg-[#ffffff] min-h-screen">
        <!-- Top Navigation Header -->
        <header
          class="w-full flex items-center justify-between py-4 px-4 sm:px-8 md:px-12 border-b border-[#f0ebe5] bg-white sticky top-0 z-10"
        >
          <!-- Back Button -->
          <button
            type="button"
            (click)="goBack()"
            class="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-[#eee7e0] bg-[#faf8f6] hover:bg-[#f3ece4] text-[#1d1b2c] text-xs font-bold transition-all shadow-xs cursor-pointer group"
            title="Back to Deliveries"
          >
            <svg
              class="w-4 h-4 text-[#a53a26] group-hover:-translate-x-0.5 transition-transform"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              stroke-width="2.5"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            <span>Back to Deliveries</span>
          </button>

          <!-- User Profile Badge -->
          <div class="flex items-center gap-3">
            <div
              class="w-8 h-8 rounded-full bg-[#a53a26] text-white flex items-center justify-center font-bold text-xs shadow-xs"
            >
              {{ customerInitials() }}
            </div>
            <span class="text-xs font-bold text-[#1d1b2c] hidden sm:inline">
              {{ customerName() }}
            </span>
          </div>
        </header>

        <!-- Page Content Body -->
        <div class="flex-1 p-4 sm:p-6 md:p-8 max-w-5xl w-full mx-auto flex flex-col h-[calc(100vh-5rem)]">
          <!-- Page Header Card -->
          <div class="bg-[#faf8f6] rounded-3xl border border-[#eee7e0] p-4 sm:p-5 mb-4 flex items-center gap-4 flex-shrink-0 shadow-xs">
            <div class="w-11 h-11 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-xs flex-shrink-0">
              <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
              </svg>
            </div>
            <div class="flex-1 min-w-0">
              <h1 class="text-lg sm:text-xl font-black text-[#1d1b2c] tracking-tight">SwiftDrop AI Assistant</h1>
              <p class="text-xs text-[#6f6d85] font-semibold mt-0.5">Ask about your parcels, delivery status, or fee estimates</p>
            </div>
            <div class="flex items-center gap-2">
              <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-[#ecfdf5] text-[#065f46] border border-[#a7f3d0]">
                <span class="w-1.5 h-1.5 rounded-full bg-[#10b981] animate-pulse"></span>
                Online
              </span>
              @if (messages().length > 0) {
                <button
                  type="button"
                  id="assistant-clear-btn"
                  (click)="clearConversation()"
                  class="text-xs text-[#6f6d85] hover:text-[#a53a26] font-bold px-3 py-1 rounded-full border border-[#eee7e0] bg-white hover:bg-[#faf8f6] transition-colors cursor-pointer"
                >
                  Clear
                </button>
              }
            </div>
          </div>

          <!-- Chat Window -->
          <div
            #scrollContainer
            class="flex-1 bg-white rounded-3xl border border-[#eee7e0] overflow-y-auto p-4 sm:p-6 space-y-4 min-h-0 shadow-xs"
            id="assistant-chat-window"
            role="log"
            aria-live="polite"
            aria-label="Conversation"
          >
            <!-- Welcome / empty state -->
            @if (messages().length === 0) {
              <div class="flex flex-col items-center justify-center h-full text-center px-6 py-10">
                <div class="w-16 h-16 rounded-3xl bg-[#faf6f0] border border-[#a53a26]/10 flex items-center justify-center mb-4 text-[#a53a26]">
                  <svg class="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                  </svg>
                </div>
                <h2 class="text-base font-black text-[#1d1b2c] mb-1">How can I help you today?</h2>
                <p class="text-xs text-[#6f6d85] font-semibold max-w-sm mb-6 leading-relaxed">
                  Ask about your parcel status, delivery history, or get an instant fee estimate.
                  I only have access to your own parcels.
                </p>
                <!-- Suggestion chips -->
                <div class="flex flex-wrap gap-2 justify-center max-w-lg" id="assistant-suggestions">
                  @for (s of suggestions; track s) {
                    <button
                      type="button"
                      (click)="useSuggestion(s)"
                      class="px-4 py-2 rounded-full text-xs font-bold text-[#1d1b2c] bg-[#faf8f6] border border-[#eee7e0] hover:border-[#a53a26] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer shadow-2xs"
                    >
                      {{ s }}
                    </button>
                  }
                </div>
              </div>
            }

            <!-- Message bubbles -->
            @for (msg of messages(); track msg.id) {
              <div class="flex gap-3 items-end" [class.flex-row-reverse]="msg.role === 'user'" [attr.id]="'msg-' + msg.id">
                <!-- Avatar -->
                <div
                  class="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-xs font-black shadow-2xs"
                  [class.bg-[#a53a26]]="msg.role === 'assistant'"
                  [class.text-white]="msg.role === 'assistant'"
                  [class.bg-[#1d1b2c]]="msg.role === 'user'"
                  [class.text-white]="msg.role === 'user'"
                >
                  {{ msg.role === 'assistant' ? 'AI' : 'You' }}
                </div>

                <!-- Bubble -->
                <div
                  class="max-w-[78%] sm:max-w-[70%] flex flex-col"
                  [class.items-end]="msg.role === 'user'"
                  [class.items-start]="msg.role === 'assistant'"
                >
                  <div
                    class="px-4.5 py-3 rounded-2xl text-xs sm:text-sm leading-relaxed whitespace-pre-wrap break-words font-medium shadow-2xs"
                    [class.bg-[#1d1b2c]]="msg.role === 'user'"
                    [class.text-white]="msg.role === 'user'"
                    [class.rounded-br-xs]="msg.role === 'user'"
                    [class.bg-[#faf8f6]]="msg.role === 'assistant' && !msg.error"
                    [class.text-[#1d1b2c]]="msg.role === 'assistant' && !msg.error"
                    [class.border]="msg.role === 'assistant' && !msg.error"
                    [class.border-[#eee7e0]]="msg.role === 'assistant' && !msg.error"
                    [class.rounded-bl-xs]="msg.role === 'assistant'"
                    [class.bg-[#fdf2f0]]="msg.error"
                    [class.text-[#a53a26]]="msg.error"
                    [class.border-[#f5d5ce]]="msg.error"
                  >
                    {{ msg.text }}
                  </div>
                  <span class="text-[10px] text-[#a09eaf] font-semibold mt-1 px-1">
                    {{ msg.timestamp | date:'h:mm a' }}
                  </span>
                </div>
              </div>
            }

            <!-- Typing indicator -->
            @if (isLoading()) {
              <div class="flex gap-3 items-end" id="assistant-typing">
                <div class="flex-shrink-0 w-8 h-8 rounded-full bg-[#a53a26] text-white flex items-center justify-center text-xs font-black shadow-2xs">
                  AI
                </div>
                <div class="px-4 py-3 rounded-2xl rounded-bl-xs bg-[#faf8f6] border border-[#eee7e0] flex items-center gap-1.5 shadow-2xs">
                  <span class="w-2 h-2 rounded-full bg-[#a53a26] animate-bounce [animation-delay:0ms]"></span>
                  <span class="w-2 h-2 rounded-full bg-[#a53a26] animate-bounce [animation-delay:150ms]"></span>
                  <span class="w-2 h-2 rounded-full bg-[#a53a26] animate-bounce [animation-delay:300ms]"></span>
                </div>
              </div>
            }
          </div>

          <!-- Input bar -->
          <div class="bg-[#faf8f6] rounded-3xl border border-[#eee7e0] p-3 sm:p-4 mt-4 flex-shrink-0 shadow-xs">
            <div class="flex gap-2 items-end">
              <div class="flex-1 relative">
                <textarea
                  id="assistant-input"
                  #inputEl
                  [(ngModel)]="inputText"
                  (keydown.enter)="onEnter($event)"
                  [disabled]="isLoading()"
                  [maxlength]="maxLength"
                  rows="1"
                  placeholder="Ask about your parcels, fees, or status…"
                  class="w-full resize-none rounded-2xl border border-[#e2e8f0] bg-white px-4 py-3 text-xs sm:text-sm font-semibold text-[#1d1b2c]
                         placeholder-[#a09eaf] focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26]
                         disabled:opacity-60 disabled:cursor-not-allowed transition-all leading-relaxed
                         max-h-32 overflow-y-auto"
                  style="field-sizing: content"
                ></textarea>
                @if (inputText.length > 0) {
                  <span class="absolute bottom-3 right-3 text-[10px] font-bold"
                    [class.text-[#6f6d85]]="inputText.length <= maxLength * 0.8"
                    [class.text-amber-500]="inputText.length > maxLength * 0.8 && inputText.length < maxLength"
                    [class.text-[#a53a26]]="inputText.length >= maxLength">
                    {{ inputText.length }}/{{ maxLength }}
                  </span>
                }
              </div>
              <button
                type="button"
                id="assistant-send-btn"
                (click)="sendMessage()"
                [disabled]="!canSend"
                class="flex-shrink-0 w-11 h-11 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center
                       hover:bg-[#8c2e1d] disabled:opacity-40 disabled:cursor-not-allowed
                       transition-all shadow-xs cursor-pointer focus:outline-none"
                aria-label="Send message">
                <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </button>
            </div>
            <p class="text-[11px] text-[#6f6d85] font-semibold mt-2 px-1">
              Press <kbd class="font-mono bg-white border border-[#eee7e0] px-1.5 py-0.5 rounded text-[10px] text-[#1d1b2c] font-bold">Enter</kbd> to send,
              <kbd class="font-mono bg-white border border-[#eee7e0] px-1.5 py-0.5 rounded text-[10px] text-[#1d1b2c] font-bold">Shift+Enter</kbd> for new line. The assistant only accesses your own parcels.
            </p>
          </div>
        </div>
      </main>
    </div>
  `,
})
export class AssistantChatComponent implements OnInit, AfterViewChecked {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router, { optional: true });
  private readonly auth = inject(AuthService, { optional: true });

  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLElement>;
  @ViewChild('inputEl') private inputEl!: ElementRef<HTMLTextAreaElement>;

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly maxLength = MAX_MESSAGE_LENGTH;
  protected readonly suggestions = SUGGESTIONS;

  protected inputText = '';
  private conversationId = '';
  private shouldScrollToBottom = false;

  protected get canSend(): boolean {
    return (
      this.inputText.trim().length > 0 &&
      this.inputText.length <= MAX_MESSAGE_LENGTH &&
      !this.isLoading()
    );
  }

  ngOnInit(): void {
    this.conversationId = 'conv-' + Math.random().toString(36).substring(2, 10);
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  protected goBack(): void {
    if (this.router) {
      this.router.navigate(['/customer']);
    }
  }

  protected logout(): void {
    if (this.auth) {
      this.auth.logout().subscribe({
        next: () => this.router?.navigate(['/login']),
        error: () => this.router?.navigate(['/login']),
      });
    } else {
      this.router?.navigate(['/login']);
    }
  }

  protected customerName = computed(() => {
    const user: any = typeof (this.auth as any)?.currentUser === 'function' ? (this.auth as any).currentUser() : null;
    return user?.fullName || user?.email?.split('@')[0] || 'Customer';
  });

  protected customerInitials = computed(() => {
    const name = this.customerName();
    if (!name) return 'CU';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  });

  protected useSuggestion(text: string): void {
    this.inputText = text;
    this.sendMessage();
  }

  protected onEnter(event: Event): void {
    const ke = event as KeyboardEvent;
    if (!ke.shiftKey) {
      event.preventDefault();
      if (this.canSend) {
        this.sendMessage();
      }
    }
  }

  protected sendMessage(): void {
    const text = this.inputText.trim();
    if (!text || this.isLoading()) return;

    this.messages.update((msgs) => [
      ...msgs,
      { id: crypto.randomUUID(), role: 'user', text, timestamp: new Date() },
    ]);
    this.inputText = '';
    this.isLoading.set(true);
    this.shouldScrollToBottom = true;

    this.http
      .post<ChatMessageResponse>('/api/v1/assistant/chat', {
        message: text,
        conversationId: this.conversationId,
      })
      .pipe(
        catchError(() =>
          of({
            reply:
              'The assistant is temporarily unavailable. Please try again later.',
            conversationId: this.conversationId,
          } as ChatMessageResponse)
        ),
        finalize(() => {
          this.isLoading.set(false);
          this.shouldScrollToBottom = true;
        })
      )
      .subscribe((response) => {
        if (response.conversationId) {
          this.conversationId = response.conversationId;
        }
        this.messages.update((msgs) => [
          ...msgs,
          {
            id: crypto.randomUUID(),
            role: 'assistant',
            text: response.reply,
            timestamp: new Date(),
          },
        ]);
      });
  }

  protected clearConversation(): void {
    this.messages.set([]);
    this.conversationId = 'conv-' + Math.random().toString(36).substring(2, 10);
  }

  private scrollToBottom(): void {
    try {
      const el = this.scrollContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch {
      // ignore
    }
  }
}
