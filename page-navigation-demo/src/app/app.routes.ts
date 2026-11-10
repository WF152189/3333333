import { Routes } from '@angular/router';
import { parentPageGuard } from './parent-page.guard';
import { ChildPageComponent, HomeComponent, ParentPageComponent } from './pages';

export const routes: Routes = [
  { path: '', component: HomeComponent, pathMatch: 'full' },
  { path: 'a', component: ParentPageComponent, canActivate: [parentPageGuard], data: { pageKey: 'A', pageId: '11001' } },
  { path: 'b', component: ParentPageComponent, canActivate: [parentPageGuard], data: { pageKey: 'B', pageId: '12341' } },
  // 子页面使用扁平路由，不继承父页面Guard。
  { path: 'a/a1', component: ChildPageComponent, data: { pageKey: 'A1', pageId: '11001' } },
  { path: 'a/a2', component: ChildPageComponent, data: { pageKey: 'A2', pageId: '11001' } },
  { path: 'b/b1', component: ChildPageComponent, data: { pageKey: 'B1', pageId: '12341', initApi: true, code: '1' } },
  { path: 'b/b2', component: ChildPageComponent, data: { pageKey: 'B2', pageId: '12341' } },
  { path: 'c/c1', component: ChildPageComponent, data: { pageKey: 'C1', pageId: '23451', initApi: true, code: '1' } },
  { path: '**', redirectTo: '' }
];
