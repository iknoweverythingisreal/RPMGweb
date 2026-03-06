import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { EventItemsService } from '../../../services/event-items.service';
import { EventService } from '../../../services/event.service';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-pending-users-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pending-users-page.component.html',
  styleUrls: ['./pending-users-page.component.scss']
})
export class PendingUsersPageComponent implements OnInit {

  pendingUsers: any[] = [];
  pendingRentals: any[] = [];
  loading = false;
  activeTab: 'users' | 'rentals' = 'users';

  roles = ['ADMIN', 'MANAGER', 'TECH_LEAD', 'TECHNICAL', 'EMPLOYEE'];

  constructor(
    private adminService: AdminService,
    private eventItemsService: EventItemsService,
    private eventService: EventService,
    public userService: UserService,
    private toastService: ToastService,
    private router: Router
  ) { }

  ngOnInit(): void {
    if (this.userService.isTechLead) {
      this.activeTab = 'rentals';
    }
    this.load();
  }

  load() {
    this.loading = true;

    // Load Users — skip for TECH_LEAD (no access to user approval API)
    if (!this.userService.isTechLead) {
      this.adminService.getPendingUsers().subscribe({
        next: (res: any) => {
          this.pendingUsers = res.pendingUsers || [];
          this.checkLoading();
        },
        error: () => this.checkLoading()
      });
    }

    // Load Rentals — always accessible
    this.eventItemsService.getPendingRentals().subscribe({
      next: (res: any[]) => {
        this.pendingRentals = res || [];
        this.checkLoading();
      },
      error: () => this.checkLoading()
    });
  }

  checkLoading() {
    // Simple check, in real app might want Promise.all
    this.loading = false;
  }

  approve(u: any, role: string) {
    this.adminService.approveUser(u.id, role).subscribe({
      next: () => this.load(),
      error: err => console.error(err)
    });
  }

  reject(u: any) {
    this.adminService.rejectUser(u.id).subscribe({
      next: () => this.load(),
      error: err => console.error(err)
    });
  }

  // Rental Actions
  approveRental(item: any) {
    const approverId = Number(localStorage.getItem('userId')) || 15;

    if (item.status === 'PENDING_RENT') {
      // External Rental Flow
      this.eventService.approveRentExternal(item.id, approverId, true, item.remark).subscribe({
        next: () => {
          this.toastService.show('✅ Rental Approved', 'success');
          this.load();
        },
        error: (err) => {
          this.toastService.show('❌ Failed to approve', 'error');
          console.error(err);
        }
      });
    } else {
      // Overbooking Flow
      this.eventItemsService.approveOverbook(item.id, approverId, item.overbookNote).subscribe({
        next: () => {
          this.toastService.show('✅ Rental Approved', 'success');
          this.load();
        },
        error: (err) => {
          this.toastService.show('❌ Failed to approve', 'error');
          console.error(err);
        }
      });
    }
  }

  rejectRental(item: any) {
    const approverId = Number(localStorage.getItem('userId')) || 15;
    if (!confirm('Are you sure you want to reject this rental request?')) return;

    if (item.status === 'PENDING_RENT') {
      // External Rental Flow
      this.eventService.approveRentExternal(item.id, approverId, false, 'Rejected by Admin').subscribe({
        next: () => {
          this.toastService.show('🗑 Rental Rejected', 'success');
          this.load();
        },
        error: (err) => {
          this.toastService.show('❌ Failed to reject', 'error');
          console.error(err);
        }
      });
    } else {
      // Overbooking Flow
      this.eventItemsService.rejectOverbook(item.id, approverId, 'Rejected by Admin').subscribe({
        next: () => {
          this.toastService.show('🗑 Rental Rejected', 'success');
          this.load();
        },
        error: (err) => {
          this.toastService.show('❌ Failed to reject', 'error');
          console.error(err);
        }
      });
    }
  }

  goBack() {
    this.router.navigate(['/calendar']);
  }
}
