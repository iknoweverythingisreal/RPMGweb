import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { EventService } from '../../../services/event.service';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../services/toast.service';

import { SerialOpsService } from '../../../services/serial-ops.service';
import { UnifiedAvailabilityService } from '../../../services/unified-availability.service';
import { EventBusService } from '../../../services/event-bus.service';

import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-event-detail-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './event-detail-page.component.html',
  styleUrls: ['./event-detail-page.component.scss']
})
export class EventDetailPageComponent implements OnInit {

  eventId!: number;
  event: any;
  eventHistory: any[] = [];
  items: any[] = [];
  isLoading = false;
  searchQuery = '';
  selectedCategory = 'ALL';

  get filteredItems() {
    let filtered = this.items;
    if (this.selectedCategory !== 'ALL') {
      filtered = filtered.filter(it => (it.category || '').toUpperCase() === this.selectedCategory.toUpperCase());
    }
    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase().trim();
      filtered = filtered.filter(it =>
        it.itemName?.toLowerCase().includes(q) ||
        it.category?.toLowerCase().includes(q)
      );
    }
    return filtered;
  }

  get substitutions() {
    return this.eventHistory.filter(h => h.changeType === 'ITEM_SUBSTITUTION');
  }

  // ================================
  // SERIAL PICKER
  // ================================
  serialPickerItem: any = null;
  unitList: any[] = [];
  pickedUnits: number[] = [];

  // ================================
  // SERIAL ACTION PANEL (Checkout / Return / Damage)
  // ================================
  actionItem: any = null;
  actionUnitList: any[] = [];
  actionPickedUnits: number[] = [];

  // ================================
  // ROLE
  // ================================
  get isManager() {
    return this.userService.isManager;
  }

  get isTech() {
    return this.userService.isTechnical;
  }

  get isTechLead() {
    return this.userService.isTechLead;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    private userService: UserService,
    private toast: ToastService,
    private serialOps: SerialOpsService,
    private unifiedService: UnifiedAvailabilityService,
    private eventBus: EventBusService
  ) { }

  // ================================
  // INIT
  // ================================
  ngOnInit(): void {
    this.eventId = Number(this.route.snapshot.paramMap.get('id'));

    this.loadEvent();
    this.loadItems();
    this.loadEventHistory();

    this.eventBus.on('serial-linked').subscribe(() => {
      this.loadItems();
      this.loadEventHistory();
      this.toast.show('🔄 Serial assignment updated from Virtual Storage');
    });
  }

  // ================================
  // LOADERS
  // ================================
  loadEvent() {
    this.eventService.getEventById(this.eventId).subscribe({
      next: (res) => this.event = res,
      error: () => this.toast.show('❌ Error loading event')
    });
  }

  loadItems() {
    this.isLoading = true;

    this.eventService.getEventItems(this.eventId).subscribe({
      next: (res) => {
        this.items = res.map((it: any) => ({
          eventItemId: it.eventItemId ?? it.id,
          itemId: it.itemId,
          itemName: it.itemName,
          requestedQuantity: it.requestedQuantity,
          allocatedQuantity: it.allocatedQuantity,
          status: it.status,
          uom: it.uom,
          serials: it.serials ?? [],
          imageUrl: it.imageUrl || `https://api.dicebear.com/7.x/shapes/svg?seed=${it.itemName}` // Placeholder
        }));
        this.isLoading = false;
        this.checkSubstitutions();
      },
      error: () => {
        this.isLoading = false;
        this.toast.show('❌ Failed to load event items');
      }
    });
  }

  loadEventItems() {
    this.eventService.getEventItems(this.eventId).subscribe({
      next: (res) => {
        this.items = res.map((it: any) => ({
          eventItemId: it.eventItemId ?? it.id,
          itemId: it.itemId,
          itemName: it.itemName,
          requestedQuantity: it.requestedQuantity,
          allocatedQuantity: it.allocatedQuantity,
          status: it.status,
          uom: it.uom,
          serials: it.serials ?? []
        }));
      }
    });
  }

  deleteItem(item: any) {
    if (!confirm(`Are you sure you want to remove ${item.itemName}?`)) return;

    this.eventService.deleteEventItem(item.eventItemId).subscribe({
      next: () => {
        this.toast.show('✅ Item removed');
        this.loadItems();
        this.loadEventHistory();
      },
      error: () => this.toast.show('❌ Failed to remove item')
    });
  }

  loadEventHistory() {
    this.eventService.getEventHistory(this.eventId).subscribe({
      next: (res) => {
        this.eventHistory = res;
        this.checkSubstitutions();
      }
    });
  }

  checkSubstitutions() {
    if (!this.items || !this.eventHistory) return;

    // A simple logic: if an item's name is in any 'ITEM_SUBSTITUTION' log's 'newItemName', mark it.
    // However, it's better to mark items that were "Swapped In".
    const substitutedNames = new Set(
      this.eventHistory
        .filter(h => h.changeType === 'ITEM_SUBSTITUTION')
        .map(h => h.data?.newItemName)
    );

    this.items.forEach(it => {
      it.isSubstituted = substitutedNames.has(it.itemName);
    });
  }

  // ================================
  // STATUS CSS
  // ================================
  getStatusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'DRAFT': return 'status-draft';
      case 'CONFIRMED': return 'status-confirmed';
      case 'READY': return 'status-ready';
      case 'CHECKED': return 'status-checked';
      case 'RENT_APPROVED': return 'status-rent';
      case 'PENDING_RENT': return 'status-pending';
      default: return 'status-other';
    }
  }

  // ================================
  // RENT FLOW
  // ================================
  openRentRequest(item: any) {
    const qty = prompt('Enter quantity to rent externally', '1');
    if (!qty) return;

    const reason = prompt('Enter reason:', 'Out of stock') ?? undefined;
    const uid = Number(localStorage.getItem('userId'));

    this.eventService
      .requestRentExternal(this.eventId, uid, item.itemId, Number(qty), reason)
      .subscribe({
        next: () => {
          this.toast.show('📝 Rent request sent');
          this.loadEventItems();
          this.loadEventHistory();
        },
        error: () => this.toast.show('❌ Failed')
      });
  }

  approveRent(item: any, approved: boolean) {
    const note = prompt('Add note', '') ?? undefined;
    const uid = Number(localStorage.getItem('userId'));

    this.eventService
      .approveRentExternal(item.eventItemId, uid, approved, note)
      .subscribe({
        next: () => {
          this.toast.show(approved ? 'Approved' : 'Rejected');
          this.loadEventItems();
          this.loadEventHistory();
        }
      });
  }

  // ================================
  // SERIAL PICKER
  // ================================
  openSerialPicker(item: any) {
    this.serialPickerItem = item;
    this.pickedUnits = [];

    const start = String(this.event?.startDate).slice(0, 10);
    const end = String(this.event?.endDate).slice(0, 10);

    this.unifiedService.getFullAvailability(
      item.itemId, this.eventId, start, end
    ).subscribe({
      next: (res) => {
        if (!res.serialMode) {
          this.toast.show('ℹ Not serial item');
          return;
        }
        this.unitList = res.serials ?? [];
      },
      error: () => this.toast.show('❌ Failed to load serials')
    });
  }

  togglePick(unitId: number, checked: boolean) {
    if (checked) this.pickedUnits.push(unitId);
    else this.pickedUnits = this.pickedUnits.filter(id => id !== unitId);
  }

  submitPickedUnits() {
    if (!this.serialPickerItem) return;

    const eventItemId =
      this.serialPickerItem.eventItemId ?? this.serialPickerItem.id;

    if (!eventItemId) {
      this.toast.show('❌ Missing eventItemId');
      return;
    }

    const req = {
      unitIds: this.pickedUnits,
      note: 'Picked from event-detail'
    };

    this.serialOps.linkUnits(this.eventId, eventItemId, req).subscribe({
      next: () => {
        this.toast.show('✅ Serials added');
        this.closePicker();
        this.loadItems();
        this.loadEventHistory();

        this.eventBus.emit('serial-linked', {
          eventId: this.eventId,
          itemId: this.serialPickerItem.itemId
        });
      },
      error: () => this.toast.show('❌ Failed')
    });
  }

  closePicker() {
    this.serialPickerItem = null;
    this.unitList = [];
    this.pickedUnits = [];
  }

  // =====================================================
  // SERIAL ACTION PANEL (Checkout / Return / Damage)
  // =====================================================
  openSerialActionPanel(item: any) {
    this.actionPickedUnits = [];
    this.actionItem = item;

    const eventItemId = item.eventItemId ?? item.id;

    this.serialOps.getUnits(this.eventId, eventItemId).subscribe({
      next: (units: any[]) => {
        this.actionUnitList = units;
      },
      error: () => this.toast.show('❌ Failed to load serial units')
    });
  }

  toggleSerialForAction(id: number, checked: boolean) {
    if (checked) this.actionPickedUnits.push(id);
    else this.actionPickedUnits = this.actionPickedUnits.filter(x => x !== id);
  }

  checkoutSelected() {
    if (!this.actionItem) return;

    const req = {
      eventItemId: this.actionItem.eventItemId ?? this.actionItem.id,
      unitIds: this.actionPickedUnits,
      note: 'Checkout from event-detail'
    };

    this.serialOps.checkout(this.eventId, req).subscribe({
      next: () => {
        this.toast.show('📦 Checked Out');
        this.afterActionRefresh();
      },
      error: () => this.toast.show('❌ Failed')
    });
  }

  returnSelected() {
    if (!this.actionItem) return;

    const req = {
      eventItemId: this.actionItem.eventItemId ?? this.actionItem.id,
      unitIds: this.actionPickedUnits,
      note: 'Returned from event-detail'
    };

    this.serialOps.doReturn(this.eventId, req).subscribe({
      next: () => {
        this.toast.show('🔄 Returned');
        this.afterActionRefresh();
      },
      error: () => this.toast.show('❌ Failed')
    });
  }

  damageSelected() {
    if (!this.actionItem) return;

    const req = {
      eventItemId: this.actionItem.eventItemId ?? this.actionItem.id,
      unitIds: this.actionPickedUnits,
      note: 'Damaged from event-detail'
    };

    this.serialOps.damage(this.eventId, req).subscribe({
      next: () => {
        this.toast.show('💥 Damaged');
        this.afterActionRefresh();
      },
      error: () => this.toast.show('❌ Failed')
    });
  }

  afterActionRefresh() {
    const itemId = this.actionItem?.itemId; // ← เก็บก่อนโดน reset

    this.closeSerialActionPanel();
    this.loadItems();
    this.loadEventHistory();

    if (itemId) {
      this.eventBus.emit('serial-linked', {
        eventId: this.eventId,
        itemId: itemId
      });
    }
  }

  closeSerialActionPanel() {
    this.actionItem = null;
    this.actionUnitList = [];
    this.actionPickedUnits = [];
  }

  // ================================
  // EVENT STATUS ACTIONS (FINAL)
  // ================================
  confirmItems() {
    const userId = Number(localStorage.getItem('userId'));

    this.eventService.confirmEventItems(this.eventId, userId).subscribe({
      next: () => {
        this.toast.show('✅ Items confirmed');
        this.loadEvent();
        this.loadItems();
        this.loadEventHistory();
      },
      error: () => this.toast.show('❌ Failed to confirm')
    });
  }


  markPrepared() {
    const userId = Number(localStorage.getItem('userId'));

    this.eventService.markPrepared(this.eventId, userId).subscribe({
      next: () => {
        this.toast.show('📦 Marked as prepared');
        this.loadEvent();
        this.loadItems();
        this.loadEventHistory();
      },
      error: () => this.toast.show('❌ Failed to update')
    });
  }

  markChecked() {
    const userId = Number(localStorage.getItem('userId'));

    this.eventService.markChecked(this.eventId, userId).subscribe({
      next: () => {
        this.toast.show('✔️ Marked as checked');
        this.loadEvent();
        this.loadItems();
        this.loadEventHistory();
      },
      error: () => this.toast.show('❌ Failed to update')
    });
  }

  // ================================
  // RETURN FLOW ACTIONS
  // ================================
  requestReturn() {
    const userId = Number(localStorage.getItem('userId'));
    if (!confirm('Request return for all checked items?')) return;

    this.eventService.requestReturn(this.eventId, userId).subscribe({
      next: (res: any) => {
        this.toast.show(res || '✅ Return requested');
        this.loadEvent();
        this.loadItems();
        this.loadEventHistory();
      },
      error: () => this.toast.show('❌ Failed to request return')
    });
  }

  approveReturn() {
    const userId = Number(localStorage.getItem('userId'));
    if (!confirm('Approve return for all requested items?')) return;

    this.eventService.approveReturn(this.eventId, userId).subscribe({
      next: (res: any) => {
        this.toast.show(res || '✅ Return approved');
        this.loadEvent();
        this.loadItems();
        this.loadEventHistory();
      },
      error: () => this.toast.show('❌ Failed to approve return')
    });
  }

  get hasCheckedItems(): boolean {
    return this.items.some(i => i.status === 'CHECKED');
  }

  get hasReturnRequestedItems(): boolean {
    return this.items.some(i => i.status === 'RETURN_REQUESTED');
  }

  goBack() {
    this.router.navigate(['/calendar']); // Default to calendar or use Location.back()
  }

  // ================================
  // NEW UI ACTIONS
  // ================================
  updateQuantity(item: any, delta: number) {
    const newQty = item.allocatedQuantity + delta;
    if (newQty < 0) return;
    if (newQty > item.requestedQuantity && item.uom === 'UNIT') {
      this.toast.show('⚠ Cannot exceed requested quantity for unit items');
      return;
    }
    item.allocatedQuantity = newQty;
    // In a real app, you'd call an API here to update the allocation
    this.toast.show(`Updated ${item.itemName} quantity to ${newQty}`);
  }

  goToCart() {
    this.router.navigate(['/inventory/event', this.eventId, 'cart']);
  }

  openGlobalRentRequest() {
    this.toast.show('ℹ Global Rent Request feature coming soon');
  }

  openAddItemModal() {
    // Navigate to inventory search page to add items
    this.router.navigate(['/inventory/event', this.eventId, 'search']);
    this.toast.show('Navigating to equipment search...');
  }

  isPastEvent(event: any): boolean {
    if (!event?.endDate) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const end = new Date(event.endDate);
    end.setHours(0, 0, 0, 0);
    return end < today;
  }

  selectEvent(itemId: number) {
    // This was used in HTML, but we are already on the event detail page.
    // Maybe navigate to item detail?
    this.router.navigate(['/inventory/event', this.eventId, 'item', itemId]);
  }
}
