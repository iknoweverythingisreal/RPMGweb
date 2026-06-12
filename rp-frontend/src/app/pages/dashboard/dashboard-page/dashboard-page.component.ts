import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardService, DashboardSummary } from '../../../services/dashboard.service';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../../services/user.service';
import { Router } from '@angular/router';
import { ToastService } from '../../../services/toast.service';
import { environment } from 'src/environments/environment';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard-page.component.html',
  styleUrls: ['./dashboard-page.component.scss']
})
export class DashboardPageComponent implements OnInit {
  summary?: DashboardSummary;
  isLoading = false;

  constructor(
    private dashboardService: DashboardService,
    private http: HttpClient,
    public userService: UserService,
    private router: Router,
    private toastService: ToastService
  ) { }

  ngOnInit() {
    this.loadSummary();
  }

  loadSummary() {
    this.isLoading = true;
    this.dashboardService.getSummary().subscribe({
      next: (data) => { this.summary = data; this.isLoading = false; },
      error: (err) => { console.error(err); this.isLoading = false; }
    });
  }

  triggerLifecycleUpdate() {
    if (!confirm('Trigger event lifecycle update now?')) return;
    this.http.post(environment.apiUrl + '/api/lifecycle/update', {}).subscribe({
      next: () => this.toastService.show('Lifecycle update completed', 'success'),
      error: (err) => this.toastService.show('Failed: ' + (err?.error?.message || err.message), 'error'),
    });
  }

  navigateToAdmin() {
    this.router.navigate(['/admin/pending-users']);
  }

}
