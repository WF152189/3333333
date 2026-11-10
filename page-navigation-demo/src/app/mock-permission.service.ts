import { HttpClient, HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { DemoStateService } from './demo-state.service';

@Injectable({ providedIn: 'root' })
export class MockPermissionService {
  private static readonly API = 'http://127.0.0.1:8080/api';
  private readonly state = inject(DemoStateService);
  private readonly http = inject(HttpClient);
  authenticateParentPage(targetPageId: string): Observable<boolean> {
    // 父页面进入前由Guard调用；响应头中的JWT代表当前父页面权限。
    this.state.log(`Guard -> Spring Boot页面认证API：${targetPageId}`);
    return this.http.post(`${MockPermissionService.API}/page-auth`, { pageId: targetPageId }, { observe: 'response' }).pipe(
      tap(response => this.saveResponseToken(response, '页面认证API成功')),
      map(() => true),
      catchError(error => this.reject(error, '页面认证API拒绝'))
    );
  }
  callBusinessApi(targetPageId: string, code: string): Observable<boolean> {
    const sourcePageId = this.state.token()?.pageId;
    if (!sourcePageId) { this.state.log('业务API拒绝：没有页面JWT，请先从菜单进入父页面A', 'error'); return of(false); }
    const endpoint = targetPageId === '12341' ? 'b' : targetPageId === '23451' ? 'c' : '';
    if (!endpoint) return of(false);
    this.state.log(`Angular -> Spring Boot业务API：source=${sourcePageId}, target=${targetPageId}, code=${code}`);
    // 后端只相信已签名JWT中的来源pageId，不相信前端另外传递的sourcePageId。
    const headers = new HttpHeaders({ 'X-Page-Authorization': `Bearer ${this.state.token()!.raw}` });
    return this.http.post(`${MockPermissionService.API}/business/${endpoint}`, { code }, { headers, observe: 'response' }).pipe(
      tap(response => {
        this.state.log('Spring AOP：PagePermissionRef、pageId、code、action验证成功');
        this.state.log('Spring Controller：正常返回');
        // 跨页面且Controller成功时后端才返回新JWT；同页面调用不会返回该响应头。
        const newToken = response.headers.get('X-Page-Authorization');
        if (newToken) this.state.saveToken(newToken, 'Controller返回后的AOP');
        else this.state.log('同一页面业务调用：后端未签发新JWT');
      }),
      map(() => true),
      catchError(error => this.reject(error, 'Spring业务API拒绝'))
    );
  }
  private saveResponseToken(response: HttpResponse<unknown>, reason: string): void {
    const token = response.headers.get('X-Page-Authorization');
    if (!token) throw new Error('后端响应没有页面JWT');
    this.state.saveToken(token, reason);
  }
  private reject(error: unknown, prefix: string): Observable<boolean> {
    const detail = error instanceof HttpErrorResponse ? `${error.status} ${error.error?.error ?? error.message}` : String(error);
    this.state.log(`${prefix}：${detail}`, 'error'); return of(false);
  }
}
