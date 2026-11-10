import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { DemoStateService } from './demo-state.service';

@Component({ selector: 'app-root', imports: [RouterOutlet, RouterLink], templateUrl: './app.html', styleUrl: './app.css' })
export class App {
  readonly state = inject(DemoStateService);
  private readonly router = inject(Router);
  reset(): void { this.state.reset(); void this.router.navigateByUrl('/'); }
}
