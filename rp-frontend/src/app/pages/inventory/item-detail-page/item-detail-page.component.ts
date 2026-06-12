import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ItemsService } from '../../../services/Item.service';
import { EventItemsService } from '../../../services/event-items.service';
import { EventService } from '../../../services/event.service';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../services/toast.service';


@Component({
  selector: 'app-item-detail-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './item-detail-page.component.html',
  styleUrls: ['./item-detail-page.component.scss']
})
export class ItemDetailPageComponent implements OnInit {

  itemId!: number;
  eventId!: number;

  /** 🔵 item เป็น normal object ไม่ใช่ signal */
  item: any = null;

  /** quantity selector */
  quantity = 1;

  /** signals (availability & history) */
  availability = signal<any>(null);
  usageEvents = signal<any[]>([]);

  isLoading = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private itemsService: ItemsService,
    private eventItemsService: EventItemsService,
    private eventService: EventService,
    private userService: UserService,
    private toastService: ToastService
  ) { }

  ngOnInit(): void {
    this.itemId = Number(this.route.snapshot.paramMap.get('itemId'));

    // Get eventId from route parameter first, fallback to localStorage
    const routeEventId = this.route.snapshot.paramMap.get('eventId');
    this.eventId = routeEventId ? Number(routeEventId) : Number(localStorage.getItem('selectedEventId') ?? 0);

    if (this.eventId) {
      localStorage.setItem('selectedEventId', String(this.eventId));
    }

    this.loadItem();
    this.loadAvailability();
    this.loadUsageHistory();
  }

  /** Load item info */
  loadItem() {
    this.itemsService.getItemById(Number(this.itemId)).subscribe({
      next: (res) => {
        // Robust name fallback
        this.item = {
          ...res,
          name: res.name || res.itemName || res.title || `Item #${res.id}`
        };
      },
      error: (err) =>
        this.toastService.show('Failed to load item: ' + (err?.error?.message || err.message), 'error'),
    });
  }

  /** Load availability for this event */
  loadAvailability() {
    if (!this.eventId) return;

    this.eventService.getEventById(this.eventId).subscribe({
      next: (ev) => {
        const params = { startDate: ev.startDate, endDate: ev.endDate };

        this.eventItemsService
          .getAvailability(params.startDate, params.endDate)
          .subscribe({
            next: (list) => {
              const found = list.find((x: any) => x.itemId == this.itemId);
              this.availability.set(found || null);
            },
          });
      },
    });
  }

  /** Load usage history */
  loadUsageHistory() {
    this.eventItemsService.getAllEventItems().subscribe({
      next: (list) => {
        const related = list.filter((x: any) => x.itemId == this.itemId);
        this.usageEvents.set(related);
      },
      error: () => console.error('❌ Failed to load usage history'),
    });
  }

  /** Quantity controls */
  changeQty(delta: number) {
    this.quantity = Math.max(1, this.quantity + delta);
  }

  /** Role check - only ADMIN/MANAGER can add to cart */
  get isManager(): boolean {
    return this.userService.isManager;
  }


  /** Add to cart */
  addToCart() {
    if (!this.item) return;

    // Check availability
    if (this.item.availableQuantity === 0) {
      this.toastService.show('This item is currently unavailable', 'error');
      return;
    }

    const raw = localStorage.getItem('cart');
    let cart = raw ? JSON.parse(raw) : [];

    const existing = cart.find((c: any) => c.itemId === this.item.id);

    if (existing) {
      existing.qty += this.quantity;
    } else {
      cart.push({
        itemId: this.item.id,
        itemName: this.item.name,
        category: this.item.category,
        uom: this.item.uom,
        qty: this.quantity,
        unitPrice: this.item.defaultPrice || 0,
        brand: this.item.brand,
        model: this.item.model,
      });
    }

    localStorage.setItem('cart', JSON.stringify(cart));
    this.toastService.show(`Added ${this.quantity} × ${this.item.name} to cart`, 'success');
  }

  /** Back */
  goBack() {
    if (this.eventId) {
      this.router.navigate(['/inventory/event', this.eventId]);
    } else {
      this.router.navigate(['/inventory/select-event']);
    }
  }
}
