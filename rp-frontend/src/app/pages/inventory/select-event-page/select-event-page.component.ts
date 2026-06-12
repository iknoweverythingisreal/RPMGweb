// src/app/pages/inventory/select-event-page/select-event-page.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { EventService, CalendarEvent } from '../../../services/event.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-select-event-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './select-event-page.component.html',
  styleUrls: ['./select-event-page.component.scss']
})
export class SelectEventPageComponent implements OnInit {
  events: CalendarEvent[] = [];
  isLoading = false;

  constructor(
    private eventService: EventService,
    private router: Router,
    private toastService: ToastService
  ) { }

  ngOnInit() {
    this.loadEvents();
  }

  loadEvents() {
    this.isLoading = true;
    this.eventService.getEvents().subscribe({
      next: (res) => {
        // กรองเฉพาะ Event ที่ Technical ต้องจัดการ
        this.events = res.filter(e =>
          ['IN_PROGRESS', 'APPROVED', 'PENDING'].includes(e.status ?? '')
        );
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.toastService.show('ไม่สามารถโหลดข้อมูล Event ได้', 'error');
      }
    });
  }

  openEvent(eventId: number) {
    this.router.navigate(['/inventory/event', eventId, 'room-assign']);
  }
}
