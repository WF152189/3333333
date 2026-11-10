import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { DemoStateService } from './demo-state.service';
import { MockPermissionService } from './mock-permission.service';
import { tap } from 'rxjs';

export const parentPageGuard: CanActivateFn = route => {
  const state = inject(DemoStateService); const permission = inject(MockPermissionService);
  const pageId = route.data['pageId'] as string;
  state.log(`父路由Guard：准备进入父页面 ${pageId}`);
  // Guard只配置在可从菜单直接进入的父页面路由上；子页面路由不执行这里。
  // 返回Observable后，Angular会等待后端页面认证API完成，再决定是否允许导航。
  return permission.authenticateParentPage(pageId).pipe(
    tap(allowed => state.log(`父路由Guard：${allowed ? '返回true，允许导航' : '返回false，取消导航'}`))
  );
};
