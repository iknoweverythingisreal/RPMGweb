import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { EventService } from '../../../services/event.service';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-equipment-availability-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './equipment-availability-page.component.html',
  styleUrls: ['./equipment-availability-page.component.scss']
})
export class EquipmentAvailabilityPageComponent implements OnInit {
  eventId!: number;
  eventTitle = '';
  items: any[] = [];
  isLoading = false;

  get isManager() {
    return this.userService.isManager;
  }

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private eventService: EventService,
    private userService: UserService,
    private toastService: ToastService
  ) { }

  ngOnInit(): void {
    this.eventId = Number(this.route.snapshot.paramMap.get('eventId'));
    this.loadAvailability();
    this.loadEventTitle();
  }

  loadAvailability() {
    this.isLoading = true;

    const now = new Date();
    const startDate = now.toISOString().split('T')[0];
    const endDate = new Date(now.getTime() + 3 * 24 * 60 * 60 * 1000)
      .toISOString()
      .split('T')[0];

    this.http
      .get<any[]>(`/api/inventory/availability?startDate=${startDate}&endDate=${endDate}`)
      .subscribe({
        next: (res) => {
          this.items = res;
          this.isLoading = false;
        },
        error: (err) => {
          console.error('❌ Failed to load availability:', err);
          this.isLoading = false;
        }
      });
  }

  loadEventTitle() {
    this.eventService.getEventById(this.eventId).subscribe({
      next: (res) => (this.eventTitle = res.title),
      error: () => (this.eventTitle = 'Unknown Event')
    });
  }

  reserveItem(item: any) {
    const qtyStr = prompt(`Enter quantity to reserve for ${item.itemName}:`, '1');
    const qty = Number(qtyStr);
    if (!qty || qty <= 0) return;

    const userId = Number(localStorage.getItem('userId'));
    this.eventService
      .reserveItem(this.eventId, item.itemId, qty, userId)
      .subscribe({
        next: () => {
          this.toastService.show(`Reserved ${qty} of ${item.itemName}`, 'success');
          this.loadAvailability();
        },
        error: (err) => {
          this.toastService.show('Failed to reserve item: ' + (err?.error?.message || err.message), 'error');
        }
      });
  }

  getAvailClass(available: number): string {
    if (available <= 0) return 'text-danger';
    if (available <= 2) return 'text-warning';
    return 'text-success';
  }
}
