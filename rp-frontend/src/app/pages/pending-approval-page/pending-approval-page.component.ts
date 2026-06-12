import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-pending-approval-page',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="pending-container">
      <div class="glass-card">
        <div class="icon-wrapper">
          <i class="fas fa-user-clock"></i>
        </div>
        <h2>Account Pending Approval</h2>
        <p>
          Your account has been created successfully but is currently waiting for administrator approval.
          <br>
          Please check back later or contact your system administrator.
        </p>
        
        <div class="actions">
          <button class="btn-secondary" (click)="logout()">
            <i class="fas fa-sign-out-alt"></i> Logout
          </button>
          <button class="btn-primary" (click)="refresh()">
            <i class="fas fa-sync-alt"></i> Check Status
          </button>
        </div>
      </div>
    </div>
  `,
    styles: [`
    .pending-container {
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
      color: white;
    }

    .glass-card {
      background: rgba(255, 255, 255, 0.05);
      backdrop-filter: blur(10px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      padding: 3rem;
      border-radius: 20px;
      text-align: center;
      max-width: 500px;
      width: 90%;
      box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
    }

    .icon-wrapper {
      font-size: 4rem;
      color: #fbbf24;
      margin-bottom: 1.5rem;
      animation: pulse 2s infinite;
    }

    h2 {
      margin-bottom: 1rem;
      font-weight: 600;
    }

    p {
      color: #9ca3af;
      line-height: 1.6;
      margin-bottom: 2rem;
    }

    .actions {
      display: flex;
      gap: 1rem;
      justify-content: center;
    }

    button {
      padding: 0.75rem 1.5rem;
      border-radius: 8px;
      border: none;
      cursor: pointer;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      transition: all 0.2s;
    }

    .btn-primary {
      background: #3b82f6;
      color: white;
    }

    .btn-primary:hover {
      background: #2563eb;
    }

    .btn-secondary {
      background: rgba(255, 255, 255, 0.1);
      color: white;
    }

    .btn-secondary:hover {
      background: rgba(255, 255, 255, 0.2);
    }

    @keyframes pulse {
      0% { transform: scale(1); opacity: 1; }
      50% { transform: scale(1.1); opacity: 0.8; }
      100% { transform: scale(1); opacity: 1; }
    }
  `]
})
export class PendingApprovalPageComponent {

    constructor(private authService: AuthService, private router: Router) { }

    logout() {
        this.authService.logout();
        this.router.navigate(['/login']);
    }

    refresh() {
        window.location.reload();
    }
}
