import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { EventItemsService } from '../../../services/event-items.service';
import { EventService } from '../../../services/event.service';

interface HistoryItem {
  id: number;
  itemName: string;
  brand?: string;
  model?: string;
  quantity: number;
  internalQty: number;
  rentalQty: number;
  confirmedAt: string;
  status: string;
  remark?: string;
}

@Component({
  selector: 'app-history-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './history-page.component.html',
  styleUrls: ['./history-page.component.scss']
})
export class HistoryPageComponent implements OnInit {
  eventId!: number;
  eventTitle: string = '';
  historyItems: HistoryItem[] = [];
  isLoading = false;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private eventItemsService: EventItemsService,
    private eventService: EventService
  ) { }

  ngOnInit() {
    // Get eventId from route
    const eventIdParam = this.route.snapshot.paramMap.get('eventId');
    if (!eventIdParam) {
      console.error('[HISTORY] No eventId in route!');
      this.router.navigate(['/inventory/select-event']);
      return;
    }

    this.eventId = Number(eventIdParam);
    this.loadEventDetails();
    this.loadHistory();
  }

  loadEventDetails() {
    this.eventService.getEventById(this.eventId).subscribe({
      next: (event) => {
        this.eventTitle = event.title || `Event #${this.eventId}`;
      },
      error: (err) => {
        console.error('[HISTORY] Failed to load event:', err);
        this.eventTitle = `Event #${this.eventId}`;
      }
    });
  }

  loadHistory() {
    this.isLoading = true;
    this.eventItemsService.getEventItems(this.eventId).subscribe({
      next: (items: any[]) => {
        const grouped = new Map<string, any>();

        items.forEach(item => {
          if (item.status === 'CANCELLED' || item.status === 'RETURNED') return;

          const key = `${item.brand || ''}|${item.model || ''}|${item.itemName || 'Unknown'}`.toLowerCase();

          if (!grouped.has(key)) {
            grouped.set(key, {
              id: item.id, // Use first ID as ref
              itemName: item.itemName,
              brand: item.brand,
              model: item.model,
              totalQty: 0,
              internalQty: 0,
              rentalQty: 0,
              status: item.status, // Default to first (simplified)
              remark: item.remark
            });
          }

          const group = grouped.get(key);
          const req = Number(item.requestedQuantity || 0);
          const alloc = Number(item.allocatedQuantity || 0);

          // Logic: 
          // Internal = Alloc
          // Rental/Shortage = Req - Alloc (if > 0) OR if it's purely external, alloc is 0 so all is rental.

          group.totalQty += req;
          group.internalQty += alloc;
          // Rental is the gap
          group.rentalQty += Math.max(0, req - alloc);
        });

        this.historyItems = Array.from(grouped.values()).map(g => ({
          id: g.id,
          itemName: g.itemName,
          brand: g.brand,
          model: g.model,
          quantity: g.totalQty,
          internalQty: g.internalQty,
          rentalQty: g.rentalQty,
          confirmedAt: new Date().toISOString(), // aggregated
          status: g.status,
          remark: g.remark
        } as any));

        this.isLoading = false;
      },
      error: (err: any) => {
        console.error('[HISTORY] Failed to load history:', err);
        this.isLoading = false;
      }
    });
  }

  goBackToInventory() {
    this.router.navigate(['/inventory/event', this.eventId]);
  }

  goToSelectEvent() {
    this.router.navigate(['/inventory/select-event']);
  }

  exportToPDF() {
    window.print();
  }
}
