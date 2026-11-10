import { Injectable, signal } from '@angular/core';
export interface PageToken { pageId: string; jti: string; raw: string; }
export interface DemoEvent { id: number; time: string; message: string; level: 'info' | 'error'; }

@Injectable({ providedIn: 'root' })
export class DemoStateService {
  private sequence = 0;
  readonly token = signal<PageToken | null>(this.readToken());
  readonly events = signal<DemoEvent[]>([]);
  log(message: string, level: 'info' | 'error' = 'info'): void {
    const event = { id: ++this.sequence, time: new Date().toLocaleTimeString('zh-CN', { hour12: false }), message, level };
    this.events.update(events => [event, ...events].slice(0, 30));
  }
  saveToken(raw: string, reason: string): void {
    // 解码仅用于界面展示；权限判断始终在Spring Boot中通过签名验证完成。
    const payload = JSON.parse(this.decodeBase64Url(raw.split('.')[1])) as { pageId: string; jti: string };
    const token = { pageId: payload.pageId, jti: payload.jti, raw };
    sessionStorage.setItem('page-token-demo', JSON.stringify(token)); this.token.set(token);
    this.log(`${reason}：保存后端签发JWT(pageId=${payload.pageId})`);
  }
  reset(): void { sessionStorage.removeItem('page-token-demo'); this.token.set(null); this.events.set([]); this.sequence = 0; }
  private readToken(): PageToken | null {
    const raw = sessionStorage.getItem('page-token-demo'); return raw ? JSON.parse(raw) as PageToken : null;
  }
  private decodeBase64Url(value: string): string {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    return decodeURIComponent(Array.from(atob(normalized), c => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''));
  }
}
