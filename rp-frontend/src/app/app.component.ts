import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './components/header/header.component';
import { Component, OnInit } from '@angular/core';
import { Router, NavigationStart } from '@angular/router';
import { UserService } from './services/user.service';
import { filter } from 'rxjs';
import { ToastComponent } from './shared/toast/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent, ToastComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  constructor(
    private userService: UserService,
    private router: Router
  ) { }

  ngOnInit(): void {
    // โหลด user จาก token ทันที
    this.userService.loadUserFromToken();

    // subscribe ไว้ให้ header/update user ใช้งาน
    this.userService.user$.subscribe(user => {
      // User loaded
    });

    // 🔹 Global Cart Cleanup: Clear cart when leaving inventory flow
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart)
    ).subscribe((event: any) => {
      const url = event.url;
      // If navigating AWAY from /inventory/event/:eventId/...
      // Note: We check if it DOES NOT start with /inventory/event/
      if (url !== '/' && !url.includes('/inventory/event/')) {
        localStorage.removeItem('cart');
        console.log('[APP] Global Cart Cleared - left inventory flow');
      }
    });
  }
}
