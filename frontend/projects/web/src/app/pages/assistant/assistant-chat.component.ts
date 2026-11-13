import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  ViewChild,
  ElementRef,
  AfterViewChecked,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { catchError, finalize, of } from 'rxjs';

export interface ChatMessage {
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

const SUGGESTIONS = [
  'Where is my parcel?',
  'What is the status of my recent delivery?',
  'Show me my recent parcels',
  'How do I track a parcel?',
];

const MAX_MESSAGE_LENGTH = 500;

/**
 * AI Delivery Assistant chat page (CU-09 / CU-10).
 *
 * Calls POST /api/v1/assistant/chat with the customer's JWT (attached
 * automatically by the shared-auth HTTP interceptor).  Customer-scoped
 * multi-tenant isolation is enforced server-side — the assistant can only
 * return information belonging to the authenticated customer.
 */
@Component({
  selector: 'app-assistant-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  template: `
    <div class="max-w-4xl mx-auto flex flex-col h-[calc(100vh-10rem)]">

      <!-- Page header -->
      <div class="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5 mb-4 flex items-center gap-4 flex-shrink-0">
        <div class="w-11 h-11 rounded-xl bg-indigo-600 flex items-center justify-center shadow-sm shadow-indigo-200 flex-shrink-0">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white"
            stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="w-5 h-5">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </div>
        <div class="flex-1 min-w-0">
          <h1 class="text-xl font-bold text-slate-900 tracking-tight">SwiftDrop AI Assistant</h1>
          <p class="text-xs text-slate-500 mt-0.5">Ask about your parcels, delivery status, or fee estimates</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold
                       bg-emerald-50 text-emerald-700 border border-emerald-200">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
            Online
          </span>
          @if (messages().length > 0) {
            <button
              type="button"
              id="assistant-clear-btn"
              (click)="clearConversation()"
              class="text-xs text-slate-500 hover:text-red-600 font-medium px-2.5 py-1 rounded-lg
                     hover:bg-red-50 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-red-400">
              Clear
            </button>
          }
        </div>
      </div>

      <!-- Chat window -->
      <div
        #scrollContainer
        class="flex-1 bg-white rounded-2xl shadow-sm border border-slate-200/80 overflow-y-auto p-4 space-y-4 min-h-0"
        id="assistant-chat-window"
        role="log"
        aria-live="polite"
        aria-label="Conversation"
      >
        <!-- Welcome / empty state -->
        @if (messages().length === 0) {
          <div class="flex flex-col items-center justify-center h-full text-center px-6 py-10">
            <div class="w-16 h-16 rounded-2xl bg-indigo-50 flex items-center justify-center mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#4f46e5"
                stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="w-8 h-8">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </div>
            <h2 class="text-base font-semibold text-slate-800 mb-1">How can I help?</h2>
            <p class="text-sm text-slate-500 max-w-xs mb-6">
              Ask about your parcel status, delivery history, or get a fee estimate.
              I only have access to your own parcels.
            </p>
            <!-- Suggestion chips -->
            <div class="flex flex-wrap gap-2 justify-center" id="assistant-suggestions">
              @for (s of suggestions; track s) {
                <button
                  type="button"
                  (click)="useSuggestion(s)"
                  class="px-3.5 py-2 rounded-xl text-sm text-indigo-700 bg-indigo-50 border border-indigo-200
                         hover:bg-indigo-100 hover:border-indigo-300 transition-colors font-medium focus:outline-none
                         focus-visible:ring-2 focus-visible:ring-indigo-400">
                  {{ s }}
                </button>
              }
            </div>
          </div>
        }

        <!-- Message bubbles -->
        @for (msg of messages(); track msg.id) {
          <div class="flex gap-3" [class.flex-row-reverse]="msg.role === 'user'" [attr.id]="'msg-' + msg.id">

            <!-- Avatar -->
            <div class="flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold"
              [class.bg-indigo-600]="msg.role === 'assistant'"
              [class.text-white]="msg.role === 'assistant'"
              [class.bg-slate-200]="msg.role === 'user'"
              [class.text-slate-600]="msg.role === 'user'">
              {{ msg.role === 'assistant' ? 'AI' : 'You' }}
            </div>

            <!-- Bubble -->
            <div class="max-w-[75%] flex flex-col"
              [class.items-end]="msg.role === 'user'"
              [class.items-start]="msg.role === 'assistant'">
              <div class="px-4 py-2.5 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap break-words"
                [class.bg-indigo-600]="msg.role === 'user'"
                [class.text-white]="msg.role === 'user'"
                [class.rounded-tr-sm]="msg.role === 'user'"
                [class.bg-slate-100]="msg.role === 'assistant' && !msg.error"
                [class.text-slate-800]="msg.role === 'assistant' && !msg.error"
                [class.rounded-tl-sm]="msg.role === 'assistant'"
                [class.bg-red-50]="msg.error"
                [class.text-red-700]="msg.error"
                [class.border]="msg.error"
                [class.border-red-200]="msg.error">
                {{ msg.text }}
              </div>
              <span class="text-[10px] text-slate-400 mt-1 px-1">
                {{ msg.timestamp | date:'h:mm a' }}
              </span>
            </div>
          </div>
        }

        <!-- Typing indicator -->
        @if (isLoading()) {
          <div class="flex gap-3" id="assistant-typing">
            <div class="flex-shrink-0 w-7 h-7 rounded-full bg-indigo-600 flex items-center justify-center text-white text-xs font-bold">
              AI
            </div>
            <div class="px-4 py-3 rounded-2xl rounded-tl-sm bg-slate-100 flex items-center gap-1.5">
              <span class="w-2 h-2 rounded-full bg-slate-400 animate-bounce [animation-delay:0ms]"></span>
              <span class="w-2 h-2 rounded-full bg-slate-400 animate-bounce [animation-delay:150ms]"></span>
              <span class="w-2 h-2 rounded-full bg-slate-400 animate-bounce [animation-delay:300ms]"></span>
            </div>
          </div>
        }
      </div>

      <!-- Input bar -->
      <div class="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-3 mt-4 flex-shrink-0">
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
              placeholder="Ask about your parcels…"
              class="w-full resize-none rounded-xl border border-slate-300 px-4 py-2.5 text-sm text-slate-900
                     placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:border-transparent
                     disabled:opacity-60 disabled:cursor-not-allowed transition-colors leading-relaxed
                     max-h-32 overflow-y-auto"
              style="field-sizing: content"
            ></textarea>
            @if (inputText.length > 0) {
              <span class="absolute bottom-2.5 right-3 text-[10px]"
                [class.text-slate-400]="inputText.length <= maxLength * 0.8"
                [class.text-amber-500]="inputText.length > maxLength * 0.8 && inputText.length < maxLength"
                [class.text-red-500]="inputText.length >= maxLength">
                {{ inputText.length }}/{{ maxLength }}
              </span>
            }
          </div>
          <button
            type="button"
            id="assistant-send-btn"
            (click)="sendMessage()"
            [disabled]="!canSend"
            class="flex-shrink-0 w-10 h-10 rounded-xl bg-indigo-600 text-white flex items-center justify-center
                   hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed
                   transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 focus-visible:ring-offset-1"
            aria-label="Send message">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="w-4 h-4">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          </button>
        </div>
        <p class="text-[10px] text-slate-400 mt-2 px-1">
          Press <kbd class="font-mono bg-slate-100 px-1 rounded">Enter</kbd> to send,
          <kbd class="font-mono bg-slate-100 px-1 rounded">Shift+Enter</kbd> for new line.
          The assistant only accesses your own parcels.
        </p>
      </div>
    </div>
  `,
})
export class AssistantChatComponent implements OnInit, AfterViewChecked {
  private readonly http = inject(HttpClient);

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

    this.messages.update(msgs => [
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
      .subscribe(response => {
        if (response.conversationId) {
          this.conversationId = response.conversationId;
        }
        this.messages.update(msgs => [
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

