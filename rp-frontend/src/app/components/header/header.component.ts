import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

type StoredUser = { id:number; email:string; name:string; role:string; calendarColor?:string };

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {
  user: StoredUser | null = JSON.parse(localStorage.getItem('currentUser') || 'null');
  get role() { return this.user?.role ?? localStorage.getItem('userRole') ?? ''; }

  constructor(private router: Router) {}

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('currentUser');
    localStorage.removeItem('userRole');
    this.router.navigateByUrl('/login');
  }
}
