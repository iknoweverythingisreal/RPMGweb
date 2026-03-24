import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EventService } from '../../../services/event.service';
import { ItemsService } from '../../../services/Item.service';
import { EventItemsService } from '../../../services/event-items.service';
import { ToastService } from '../../../services/toast.service';

interface CartItem {
  id: string; // Aggregate ID
  itemName: string;
  brand?: string;
  model?: string;
  category: string;
  uom: string;
  qty: number;
  room?: string; // New field
  unitPrice?: number;
  description?: string;
  isForcedRental?: boolean;
  individualIds: { itemId: number, qty: number, status?: string, source?: string, autoApprove?: boolean }[];
}

@Component({
  selector: 'app-cart-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cart-page.component.html',
  styleUrls: ['./cart-page.component.scss']
})
export class CartPageComponent implements OnInit {
  eventId!: number;
  cart = signal<CartItem[]>([]);
  total = signal<number>(0);
  eventName = signal<string>('Loading...');
  showConfirmModal = signal<boolean>(false);
  availabilityMap = signal<Map<string, any>>(new Map()); // Key is aggregate ID
  bookedMap = signal<Map<number, number>>(new Map()); // Key is individual itemId
  pendingRentals = signal<any[]>([]); // Items already saved to DB with PENDING_RENT status
  showRentals = signal<boolean>(true); // Toggle for collapsible section
  showRentalModal = signal<boolean>(false);
  categories = [
    { id: 'all', name: { en: 'All', th: 'ทั้งหมด' }, icon: '📦' },
    { id: 'SOUND', name: { en: 'Sound', th: 'เสียง' }, icon: '🔊' },
    { id: 'LED', name: { en: 'LED', th: 'LED' }, icon: '💡' },
    { id: 'VISUAL', name: { en: 'Visual', th: 'ภาพ' }, icon: '📺' },
    { id: 'LIGHTING', name: { en: 'Lighting', th: 'แสงสว่าง' }, icon: '💡' },
    { id: 'IT', name: { en: 'IT', th: 'ไอที' }, icon: '💻' }
  ];

  rentalRequest = {
    category: 'SOUND',
    brandModel: '',
    itemName: '',
    qty: 1,
    price: 0,
    remark: ''
  };
  eventDates: { start: string, end: string } | null = null;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private eventService: EventService,
    private itemsService: ItemsService,
    private eventItemsService: EventItemsService,
    private toastService: ToastService
  ) { }

  ngOnInit() {
    console.log('[CART] ngOnInit - eventId from route/local:', this.eventId);
    this.loadCart();

    const routeEventId = this.route.snapshot.paramMap.get('eventId');
    this.eventId = routeEventId ? Number(routeEventId) : Number(localStorage.getItem('selectedEventId') ?? 0);

    if (this.eventId) {
      localStorage.setItem('selectedEventId', String(this.eventId));
      this.fetchEventName();
    } else {
      console.warn('[CART] No eventId found!');
    }

    this.updateTotal();
  }

  loadCart() {
    const raw = localStorage.getItem('cart');
    if (!raw) return;
    const items: any[] = JSON.parse(raw);

    // Group items by name + brand + model + room
    const grouped = new Map<string, CartItem>();
    items.forEach(it => {
      const roomName = it.room || '';
      const key = `${it.brand || ''}|${it.model || ''}|${it.itemName}|${roomName}`.toLowerCase();

      if (!grouped.has(key)) {
        grouped.set(key, {
          id: key,
          itemName: it.itemName,
          brand: it.brand,
          model: it.model,
          category: it.category,
          uom: it.uom,
          qty: it.qty,
          room: roomName,
          unitPrice: it.unitPrice || 0,
          description: it.description || '',
          individualIds: [{ itemId: it.itemId, qty: it.qty, status: it.status, source: it.source, autoApprove: it.autoApprove } as any]
        });
      } else {
        const group = grouped.get(key)!;
        group.qty += it.qty;
        group.individualIds.push({ itemId: it.itemId, qty: it.qty, status: it.status, source: it.source, autoApprove: it.autoApprove } as any);
      }
    });

    this.cart.set(Array.from(grouped.values()));
    this.runAvailabilityCalculation();
  }

  fetchEventName() {
    console.log('[CART] fetchEventName for eventId:', this.eventId);
    this.eventService.getEventById(this.eventId).subscribe({
      next: (ev) => {
        console.log('[CART] Event data received:', ev);
        this.eventName.set(ev.title || 'Event');
        if (ev.startDate && ev.endDate) {
          // Format dates to YYYY-MM-DD if they are ISO strings
          const start = ev.startDate.includes('T') ? ev.startDate.split('T')[0] : ev.startDate;
          const end = ev.endDate.includes('T') ? ev.endDate.split('T')[0] : ev.endDate;

          this.eventDates = { start, end };
          console.log('[CART] Parsed eventDates:', this.eventDates);
          this.fetchAvailability();
        } else {
          console.warn('[CART] Event is missing startDate or endDate!', ev);
          // Fallback: run calculation even without dates to show at least something
          this.runAvailabilityCalculation();
        }
      },
      error: (err) => {
        console.error('[CART] Error fetching event:', err);
        this.eventName.set('Event #' + this.eventId);
        this.runAvailabilityCalculation(); // Fallback
      }
    });
  }

  // Reactive storage for raw availability data
  private rawAvailability = signal<any[]>([]);

  fetchAvailability() {
    if (!this.eventDates) return;
    const { start, end } = this.eventDates;

    console.log('[CART] Fetching availability for:', start, end);

    // 1. Fetch event items (for booked counts and existing rentals)
    this.eventItemsService.getEventItems(this.eventId).subscribe((bookedItems: any[]) => {
      const bookedCounts = new Map<number, number>();
      const rentals: any[] = [];

      bookedItems.forEach((bi: any) => {
        if (bi.status !== 'CANCELLED' && bi.status !== 'RETURNED') {
          const current = bookedCounts.get(bi.itemId) || 0;
          bookedCounts.set(bi.itemId, current + (bi.allocatedQuantity || 0));

          if (bi.status === 'PENDING_RENT' || bi.source === 'RENT_EXTERNAL') {
            rentals.push(bi);
          }
        }
      });
      this.bookedMap.set(bookedCounts);
      this.pendingRentals.set(rentals);

      // 2. Fetch availability only for items in the current cart (targeted, fast)
      const cartItemSet = new Set<number>();
      this.cart().forEach(item => item.individualIds.forEach(ind => cartItemSet.add(ind.itemId)));
      const cartItemIds = Array.from(cartItemSet);
      if (cartItemIds.length === 0) {
        this.rawAvailability.set([]);
        this.runAvailabilityCalculation();
        return;
      }
      this.eventItemsService.getBulkAvailability(cartItemIds, start, end, this.eventId).subscribe((data: any[]) => {
        console.log('[CART] Warehouse data received:', data.length, 'items (bulk, cart-only)');
        this.rawAvailability.set(data);
        this.runAvailabilityCalculation();
      });
    });
  }

  // Split calculation so it can be re-run on local quantity change
  private runAvailabilityCalculation() {
    const rawAvail = this.rawAvailability() || [];
    console.log('[CART] runAvailabilityCalculation started. RawAvail size:', rawAvail.length);

    // ⚠️ API returns `itemId` (not `id`) and `totalQuantity` (not `total`)
    const idToAvail = new Map<number, any>();
    rawAvail.forEach(a => idToAvail.set(Number(a.itemId), a));

    const bookedCounts = this.bookedMap();
    const groupMap = new Map<string, any>();

    this.cart().forEach(group => {
      let sumTotal = 0;        // Company's total stock count
      let sumAvailable = 0;    // Total warehouse stock available for this event (total - commitments from OTHER events)
      let sumNetAvail = 0;     // Available AFTER subtracting current event's existing bookings + current cart qty
      let sumOverbooked = 0;   // How much the total event request (saved + cart) exceeds warehouse stock
      let hasAvailData = false;

      group.individualIds.forEach(indv => {
        const avail = idToAvail.get(Number(indv.itemId));
        if (avail) hasAvailData = true;

        // Rental items don't use warehouse stock so they must NEVER contribute to overbooked count
        const isRental = (indv as any).status === 'PENDING_RENT' || (indv as any).source === 'RENT_EXTERNAL' || (indv as any).autoApprove === false;
        if (isRental) {
          (group as any).isForcedRental = true;
          return;
        }

        // Warehouse capacity (Total Stock - Bookings from OTHER events)
        const warehouseCapacity = avail ? (Number(avail.available) || 0) : 0;
        const totalStock = avail ? (Number(avail.totalQuantity) || 0) : 0;

        // Already Booked for THIS event
        const alreadyBooked = bookedCounts.get(Number(indv.itemId)) || 0;

        // Stock remaining AFTER current bookings (but before cart)
        const remainingStock = Math.max(0, warehouseCapacity - alreadyBooked);

        // Net available after accounting for cart qty
        const netAvail = remainingStock - indv.qty;

        // Overbooked = how much the total demand (existing + cart) exceeds warehouse capacity
        const over = Math.max(0, (alreadyBooked + indv.qty) - warehouseCapacity);

        sumTotal += totalStock;
        sumAvailable += remainingStock; // This will be the "STOCK" badge in UI
        sumNetAvail += netAvail;
        sumOverbooked += over;
      });

      groupMap.set(group.id, {
        total: sumTotal, // COMPANY TOTAL STOCK (hidden/different badge context)
        remainingStock: sumAvailable, // ACTUAL STOCK AVAILABLE FOR CART ITEMS
        netAvail: sumNetAvail, // AVAIL badge (remaining after cart)
        overbooked: sumOverbooked, // OVERBOOKED badge
        hasData: hasAvailData
      });
    });

    console.log('[CART] Final availabilityMap keys:', Array.from(groupMap.keys()));
    this.availabilityMap.set(groupMap);
  }

  updateTotal() {
    this.total.set(
      this.cart().reduce((sum, i) => sum + (i.unitPrice ?? 0) * i.qty, 0)
    );
  }

  removeItem(groupId: string) {
    // 1. Remove from local signal
    const updated = this.cart().filter(i => i.id !== groupId);
    this.cart.set(updated);

    // 2. Clear from localStorage (we need to flatten it back)
    this.saveToStorage(updated);
    this.updateTotal();
  }

  private saveToStorage(groupedItems: CartItem[]) {
    const flat = groupedItems.flatMap(g =>
      g.individualIds.map(indv => ({
        itemId: indv.itemId,
        itemName: g.itemName,
        brand: g.brand,
        model: g.model,
        category: g.category,
        uom: g.uom,
        qty: indv.qty, // Note: if we changed total qty, this needs to be redistributed
        source: indv.source,
        unitPrice: g.unitPrice,
        description: g.description
      }))
    );
    localStorage.setItem('cart', JSON.stringify(flat));
  }

  changeQty(groupId: string, delta: number) {
    const group = this.cart().find(i => i.id === groupId);
    if (!group) return;

    const newQty = group.qty + delta;
    if (newQty < 1) return;

    group.qty = newQty;
    this.syncGroupIndvQty(group, delta);
    this.saveToStorage(this.cart());
    this.updateTotal();
    this.runAvailabilityCalculation();
  }

  onQtyInput(groupId: string, event: any) {
    const group = this.cart().find(i => i.id === groupId);
    if (!group) return;

    let val = parseInt(event.target.value);
    if (isNaN(val) || val < 1) {
      val = 1;
    }
    if (val > 999) val = 999;

    const delta = val - group.qty;
    group.qty = val;
    event.target.value = val;

    this.syncGroupIndvQty(group, delta);
    this.saveToStorage(this.cart());
    this.updateTotal();
    this.runAvailabilityCalculation();
  }

  private syncGroupIndvQty(group: CartItem, delta: number) {
    if (delta > 0) {
      group.individualIds[0].qty += delta;
    } else if (delta < 0) {
      let toRemove = Math.abs(delta);
      for (const indv of group.individualIds) {
        const sub = Math.min(indv.qty, toRemove);
        indv.qty -= sub;
        toRemove -= sub;
        if (toRemove <= 0) break;
      }
    }
  }

  clearCart(force: boolean = false) {
    if (!force) {
      // For MVP, we'll just clear it. In production, use a custom modal.
    }
    this.cart.set([]);
    localStorage.removeItem('cart');
    this.total.set(0);
  }

  goBack() {
    this.router.navigate(['/inventory/event', this.eventId]);
  }

  openConfirmModal() {
    if (!this.cart().length) return;
    this.showConfirmModal.set(true);
  }

  closeConfirmModal() {
    this.showConfirmModal.set(false);
  }

  isPastEvent(): boolean {
    if (!this.eventDates) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const end = new Date(this.eventDates.end);
    end.setHours(0, 0, 0, 0);
    return end < today;
  }

  openRentalModal(item?: any) {
    // ⭐ Redirect: Consolidate rental logic to Inventory page
    this.router.navigate(['/inventory/event', this.eventId], {
      queryParams: { openRental: 'true' }
    });
  }

  proceedToAllocation() {
    if (!this.eventId) {
      this.toastService.show('❌ No event selected!', 'error');
      return;
    }

    this.closeConfirmModal();

    // 🔹 If cart is empty, just navigate if items already exist in DB
    if (this.cart().length === 0) {
      this.navigateToRoomDirector();
      return;
    }

    // Validation: Ensure no 0 quantities
    const hasInvalidQty = this.cart().some(g => g.qty <= 0);
    if (hasInvalidQty) {
      this.toastService.show('❌ Some items have invalid quantities (0). Please remove or update them.', 'error');
      return;
    }

    // Mapping ALL individual IDs to the request
    const items: any[] = [];
    this.cart().forEach(group => {
      group.individualIds.forEach(indv => {
        if (indv.qty > 0) {
          items.push({
            itemId: indv.itemId,
            requestedQuantity: indv.qty,
            unitPrice: group.unitPrice || 0,
            rateType: 'daily',
            remark: group.description || group.itemName || '',
            status: indv.status,
            source: indv.source,
            autoApprove: (indv as any).autoApprove,
            metadata: {
              room: group.room || ''
            }
          });
        }
      });
    });

    console.log('[CART] Syncing items to event:', this.eventId, items);

    this.eventItemsService.addBulkItemsToEvent(this.eventId, items).subscribe({
      next: (res) => {
        this.toastService.show('✅ Items synced successfully', 'success');

        // 🔹 Clear cart (localStorage) now that sync is successful
        localStorage.removeItem('cart');

        setTimeout(() => {
          this.navigateToRoomDirector();
        }, 800);
      },
      error: (err) => {
        console.error('[CART] API Error:', err);
        this.toastService.show('❌ Failed to sync: ' + (err?.error?.message || err.message), 'error');
      }
    });
  }

  navigateToRoomDirector() {
    const targetUrl = ['/inventory/event', this.eventId.toString(), 'room-assign'];
    this.router.navigate(targetUrl);
  }

  isExternal(item: any): boolean {
    if (!item) return false;
    // For cart items (aggregated)
    if (item.isForcedRental) return true;
    const cat = item.category || '';
    const name = (item.itemName || '').toLowerCase();
    return cat.includes('RENTAL') || cat === 'OTHER' || name.includes('external');
  }

  isNote(item: any): boolean {
    if (!item) return false;
    const remark = item.remark || item.overbookNote || '';
    return remark.startsWith('###NOTE###');
  }

  getNoteContent(item: any): string {
    const remark = item.remark || item.overbookNote || '';
    return remark.replace('###NOTE###', '');
  }

  getTitle(item: any): string {
    const brand = item.brand || item.item?.brand || '';
    const model = item.model || item.item?.model || '';
    const brandModel = `${brand} ${model}`.trim();

    if (this.isNote(item)) return item.itemName || 'Untitled';

    // If we have brand/model, that's the primary title
    if (brandModel) return brandModel;

    const name = item.itemName || item.item?.name || 'Untitled';
    const remark = item.remark || '';
    if (this.isExternal(item)) {
      if (remark.includes(' | ')) return remark.split(' | ')[0];
      if (remark && !remark.includes('Rental')) return remark;
    }
    return name;
  }

  getDescription(item: any): string {
    if (this.isNote(item)) {
      return this.getNoteContent(item);
    }

    const title = this.getTitle(item);
    const itemName = item.itemName || '';

    // If title is Brand/Model, and itemName is something else (like Category), show it as description
    if (title !== itemName && itemName && !title.includes(itemName)) {
      return itemName;
    }

    const remark = item.remark || item.overbookNote || '';
    if (this.isExternal(item) && remark.includes(' | ')) {
      return remark.split(' | ')[1];
    }
    return item.description || item.item?.description || '';
  }
}
