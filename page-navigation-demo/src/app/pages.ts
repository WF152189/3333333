import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DemoStateService } from './demo-state.service';
import { MockPermissionService } from './mock-permission.service';

@Component({
  standalone: true,
  template: `
    <div class="page-card intro">
      <h2>测试顺序</h2>
      <ol>
        <li>点击顶部“父页面 A”：确认Guard调用页面认证API，JWT变为11001。</li>
        <li>进入A1，再进入A2：确认不调用Guard，JWT保持11001。</li>
        <li>从A1进入“父页面B”：确认B父路由Guard认证，JWT变为12341。</li>
        <li>重置后重新进入A→A1，再进入B1：确认不调用Guard，由B业务API验证A→B并更新JWT。</li>
        <li>从B1进入C1：确认使用JWT(B)验证B→C。</li>
      </ol>
      <p class="warning">子页面路由在本Demo中采用扁平配置，确保A1→B1不会触发B父路由Guard。</p>
    </div>`
})
export class HomeComponent {}

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page-card parent">
      <span class="badge">父页面</span><h2>父页面 {{ pageKey }}</h2><p>pageId={{ pageId }}</p>
      <p>进入此页面前，父路由Guard已经调用页面认证API。</p>
      @if (pageKey === 'A') { <a class="button" routerLink="/a/a1">进入A1子页面</a> }
      @if (pageKey === 'B') { <a class="button" routerLink="/b/b1">进入B1子页面</a> }
    </div>`
})
export class ParentPageComponent {
  private readonly route = inject(ActivatedRoute);
  readonly pageKey = this.route.snapshot.data['pageKey'] as string;
  readonly pageId = this.route.snapshot.data['pageId'] as string;
}

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page-card child">
      <span class="badge">子页面</span><h2>子页面 {{ pageKey }}</h2><p>继承pageId={{ pageId }}</p>
      @if (apiResult !== null) { <p [class.success]="apiResult" [class.failure]="!apiResult">初始化业务API：{{ apiResult ? '成功' : '失败' }}</p> }
      <div class="actions">
        @if (pageKey === 'A1') {
          <a class="button" routerLink="/a/a2">A1 → A2（同父页面）</a>
          <a class="button" routerLink="/b">A1 → B父页面（触发Guard）</a>
          <a class="button accent" routerLink="/b/b1">A1 → B1（API层认证）</a>
        }
        @if (pageKey === 'A2') { <a class="button" routerLink="/a/a1">返回A1</a> }
        @if (pageKey === 'B1') {
          <button (click)="callCurrentApi()">调用B当前页面业务API</button>
          <a class="button accent" routerLink="/c/c1">B1 → C1（连续关联）</a>
          <a class="button" routerLink="/b/b2">B1 → B2（同父页面）</a>
        }
        @if (pageKey === 'B2') { <a class="button" routerLink="/b/b1">返回B1</a> }
        @if (pageKey === 'C1') { <button (click)="callCurrentApi()">调用C当前页面业务API</button> }
      </div>
    </div>`
})
export class ChildPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly state = inject(DemoStateService);
  private readonly permission = inject(MockPermissionService);
  readonly pageKey = this.route.snapshot.data['pageKey'] as string;
  readonly pageId = this.route.snapshot.data['pageId'] as string;
  apiResult: boolean | null = null;

  ngOnInit(): void {
    this.state.log(`Angular Router：进入子页面${this.pageKey}，未执行父路由Guard`);
    if (this.route.snapshot.data['initApi']) {
      // A1->B1等跨子页面跳转依靠目标业务API认证，而不是父页面Guard。
      this.permission.callBusinessApi(this.pageId, this.route.snapshot.data['code'] as string)
        .subscribe(result => this.apiResult = result);
    }
  }
  callCurrentApi(): void {
    this.permission.callBusinessApi(this.pageId, '1').subscribe(result => this.apiResult = result);
  }
}
