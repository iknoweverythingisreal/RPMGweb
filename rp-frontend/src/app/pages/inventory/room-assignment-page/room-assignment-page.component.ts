import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { EventService } from '../../../services/event.service';
import { EventItemsService } from '../../../services/event-items.service';
import { ToastService } from '../../../services/toast.service';
import { UserService } from '../../../services/user.service';

@Component({
  selector: 'app-room-assignment-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './room-assignment-page.component.html',
  styleUrls: ['./room-assignment-page.component.scss']
})
export class RoomAssignmentPageComponent implements OnInit {
  eventId!: number;
  eventName = signal<string>('Loading...');
  eventData: any = null;

  items = signal<any[]>([]);
  rooms = signal<string[]>([]);
  isLoading = signal<boolean>(false);

  // Zero-Friction UX: Active Room Logic
  activeRoomName = signal<string | null>(null);
  mobileTab = signal<'pool' | 'rooms'>('pool'); // Toggle between Pool and Designer on mobile

  // Progress Tracking
  totalQty = signal<number>(0);
  assignedQty = signal<number>(0);

  categories = [
    { id: 'all', name: { en: 'All', th: 'ทั้งหมด' }, icon: '📦' },
    { id: 'SOUND', name: { en: 'Sound', th: 'เสียง' }, icon: '🔊' },
    { id: 'LED', name: { en: 'LED', th: 'LED' }, icon: '💡' },
    { id: 'VISUAL', name: { en: 'Visual', th: 'ภาพ' }, icon: '📺' },
    { id: 'LIGHTING', name: { en: 'Lighting', th: 'แสงสว่าง' }, icon: '💡' },
    { id: 'IT', name: { en: 'IT', th: 'ไอที' }, icon: '💻' }
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    public eventItemsService: EventItemsService,
    public userService: UserService,
    private toastService: ToastService
  ) { }

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.eventId = +params['eventId'];
      this.loadAll();
    });
  }

  loadAll() {
    this.isLoading.set(true);
    this.eventService.getEventById(this.eventId).subscribe({
      next: (ev) => {
        this.eventData = ev;
        this.eventName.set(ev.title || 'Event');
        const customRooms = ev.customFields?.['rooms'] || [];
        this.rooms.set(customRooms);
        if (customRooms.length > 0 && !this.activeRoomName()) {
          this.activeRoomName.set(customRooms[0]); // Auto-select first room
        }
        this.loadItems();
      },
      error: (err) => {
        this.toastService.show(this.getErrorMessage(err), 'error');
        this.isLoading.set(false);
      }
    });
  }

  loadItems() {
    this.eventItemsService.getEventItems(this.eventId).subscribe({
      next: (data) => {
        let total = 0;
        let assigned = 0;

        const enhanced = data.map(it => {
          const requested = Number(it.requestedQuantity || 0);
          const allocated = Number(it.allocatedQuantity || 0);
          total += requested;
          if (it.room && it.room !== 'Unassigned') {
            assigned += requested;
          }

          // An item is rental if: explicitly from external rental source, status says rental,
          // OR allocatedQuantity is 0 (no stock pulled — must be rented)
          const isRentalSource = it.source === 'RENT_EXTERNAL'
            || it.status === 'PENDING_RENT'
            || it.status === 'RENTED';
          const isAutoRental = !isRentalSource && allocated === 0 && requested > 0;

          return {
            ...it,
            requestedQuantity: requested,
            allocatedQuantity: allocated,
            moveQty: requested, // Default to move all for one-click efficiency
            isStockOnly: !isRentalSource && !isAutoRental && allocated > 0 && allocated === requested,
            isRentalOnly: isRentalSource || isAutoRental,
            isShortage: !isRentalSource && !isAutoRental && allocated > 0 && allocated < requested
          };
        });

        this.items.set(enhanced);
        this.totalQty.set(total);
        this.assignedQty.set(assigned);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.toastService.show(this.getErrorMessage(err), 'error');
        this.isLoading.set(false);
      }
    });
  }

  // --- Core UX Actions ---

  selectActiveRoom(room: string) {
    this.activeRoomName.set(room);
  }

  // --- Role Helpers ---
  get isAdmin(): boolean {
    return this.userService.isAdmin;
  }

  get isManager(): boolean {
    return this.userService.isManager;
  }

  get isTechnical(): boolean {
    return this.userService.isTechnical;
  }

  onQtyChange(item: any, event: any) {
    let val = parseInt(event.target.value);
    const maxVal = item.room && item.room !== 'Unassigned' ? item.requestedQuantity : 999;

    // Strict Validation: Force at least 1
    if (isNaN(val) || val < 1) {
      val = 1;
      this.toastService.show('⚠️ Quantity must be at least 1', 'warning');
    }

    if (val > maxVal) {
      val = maxVal;
      this.toastService.show(`⚠️ Maximum quantity reached (${maxVal})`, 'warning');
    }

    item.moveQty = val;
    event.target.value = val;
  }

  getCategoryIcon(catName: string): string {
    const cat = this.categories.find(c => c.id === catName || c.name.en === catName);
    return cat ? cat.icon : '📦';
  }

  quickAssign(item: any) {
    const targetRoom = this.activeRoomName();
    if (!targetRoom) {
      this.toastService.show('Please select or create a room first', 'warning');
      return;
    }
    const qty = item.moveQty || item.requestedQuantity;
    if (qty <= 0) {
      this.toastService.show('Quantity must be at least 1', 'warning');
      return;
    }
    this.executeMove(item, targetRoom);
  }

  executeMove(item: any, targetRoom: string) {
    this.isLoading.set(true);
    const qty = Math.floor(item.moveQty || item.requestedQuantity);

    this.eventItemsService.assignToRoom(item.id, targetRoom, qty).subscribe({
      next: () => {
        this.loadItems(); // Reload to refresh lists and progress
      },
      error: (err) => {
        this.toastService.show(this.getErrorMessage(err), 'error');
        this.isLoading.set(false);
      }
    });
  }

  unassign(item: any) {
    const qty = item.moveQty || item.requestedQuantity;
    this.executeMove({ ...item, moveQty: qty }, 'Unassigned');
  }

  editRemark(item: any) {
    const currentRemark = item.remark || item.overbookNote || '';
    const newRemark = prompt('Edit Remark / Custom Name:', currentRemark);
    if (newRemark !== null) {
      this.saveRemark(item, newRemark);
    }
  }

  saveRemark(item: any, newRemark: string) {
    this.isLoading.set(true);

    let remark = newRemark;
    let customName: string | undefined = newRemark;
    let customDescription: string | undefined = undefined;

    // 🔹 If it's a Handover Note, ensure the prefix is preserved if they didn't type it
    if (this.isNote(item)) {
      if (!remark.startsWith('###NOTE###')) {
        remark = '###NOTE###' + remark;
      }
      customName = undefined; // For notes, we typically use the remark as the primary source or keep customName static
    }

    // Directly update the details on the existing backend record
    this.eventItemsService.updateRemark(item.id, remark, customName, customDescription).subscribe({
      next: () => {
        this.toastService.show('✅ Item updated', 'success');
        this.loadItems();
      },
      error: (err: any) => {
        this.toastService.show('❌ Failed to update item', 'error');
        this.isLoading.set(false);
      }
    });
  }

  returnToCart(item: any) {
    if (!this.isManager) {
      this.toastService.show('🚫 Only Managers/Admins can remove items from event', 'error');
      return;
    }

    if (!confirm(`Return "${item.itemName}" to cart? It will be removed from this event's pool.`)) {
      return;
    }

    this.isLoading.set(true);
    // 1. Remove from Backend
    this.eventItemsService.deleteEventItem(item.id).subscribe({
      next: () => {
        // 2. Add back to Local Cart
        const raw = localStorage.getItem('cart');
        const cartItems: any[] = raw ? JSON.parse(raw) : [];

        cartItems.push({
          itemId: item.itemId,
          itemName: this.getTitle(item),
          brand: item.item?.brand || '',
          model: item.item?.model || '',
          category: item.categoryName || item.item?.category?.name || 'Accessories',
          uom: item.item?.uom || 'Unit',
          qty: item.requestedQuantity,
          unitPrice: item.individualItems?.[0]?.unitPrice || 0, // Try to preserve price if possible
          description: this.getDescription(item) || item.item?.description || ''
        });

        localStorage.setItem('cart', JSON.stringify(cartItems));

        this.toastService.show('✅ Item returned to cart', 'success');
        this.loadItems(); // Refresh Pool
      },
      error: (err) => {
        this.toastService.show('❌ Failed to return to cart: ' + this.getErrorMessage(err), 'error');
        this.isLoading.set(false);
      }
    });
  }

  deleteAllUnassigned() {
    if (!this.isManager) {
      this.toastService.show('🚫 Only Managers/Admins can delete items', 'error');
      return;
    }

    const unassigned = this.getUnassignedItems();
    if (unassigned.length === 0) {
      this.toastService.show('Pool is already empty', 'info');
      return;
    }

    if (!confirm(`Return ALL ${unassigned.length} unassigned items to cart? This removes them from the event pool.`)) {
      return;
    }

    this.isLoading.set(true);
    const raw = localStorage.getItem('cart');
    const cartItems: any[] = raw ? JSON.parse(raw) : [];

    const deletePromises = unassigned.map(item =>
      this.eventItemsService.deleteEventItem(item.id).toPromise().then(() => {
        // Restore item to local cart
        cartItems.push({
          itemId: item.itemId,
          itemName: this.getTitle(item),
          brand: item.item?.brand || '',
          model: item.item?.model || '',
          category: item.categoryName || item.item?.category?.name || 'Accessories',
          uom: item.item?.uom || 'Unit',
          qty: item.requestedQuantity,
          unitPrice: 0,
          description: this.getDescription(item) || item.item?.description || ''
        });
      })
    );

    Promise.all(deletePromises).then(() => {
      localStorage.setItem('cart', JSON.stringify(cartItems));
      this.toastService.show(`🛒 Returned ${unassigned.length} items to cart`, 'success');
      this.loadItems();
    }).catch(() => {
      localStorage.setItem('cart', JSON.stringify(cartItems));
      this.toastService.show('⚠️ Some items failed to delete', 'error');
      this.loadItems();
    });
  }

  // --- UI Helpers ---
  getUnassignedItems() {
    return this.items().filter(it => !it.room || it.room === 'Unassigned');
  }

  getPoolCategories() {
    const items = this.getUnassignedItems();
    const cats = new Set<string>();
    items.forEach(it => {
      const cat = it.categoryName || it.item?.category?.name || 'Accessories';
      cats.add(cat);
    });
    return Array.from(cats).sort();
  }

  getItemsInCategory(cat: string) {
    return this.getUnassignedItems().filter(it => {
      const itemCat = it.categoryName || it.item?.category?.name || 'Accessories';
      return itemCat === cat;
    });
  }

  getItemsInRoom(roomName: string) {
    return this.items()
      .filter(it => it.room === roomName)
      .sort((a, b) => {
        // Pure Stock first, then Rental
        const aIsRental = Number(a.allocatedQuantity || 0) === 0;
        const bIsRental = Number(b.allocatedQuantity || 0) === 0;
        if (aIsRental !== bIsRental) {
          return aIsRental ? 1 : -1;
        }
        return (a.itemName || '').localeCompare(b.itemName || '');
      });
  }

  getRoomTotalQty(roomName: string): number {
    return this.items()
      .filter(it => it.room === roomName)
      .reduce((sum, it) => sum + Number(it.requestedQuantity || 0), 0);
  }

  getRoomStockQty(roomName: string): number {
    return this.items()
      .filter(it => it.room === roomName)
      .reduce((sum, it) => sum + Number(it.allocatedQuantity || 0), 0);
  }

  getRoomRentalQty(roomName: string): number {
    return this.items()
      .filter(it => it.room === roomName)
      .reduce((sum, it) => {
        const req = Number(it.requestedQuantity || 0);
        const alloc = Number(it.allocatedQuantity || 0);
        return sum + Math.max(0, req - alloc);
      }, 0);
  }

  getProgressPercentage(): number {
    return this.totalQty() > 0 ? Math.round((this.assignedQty() / this.totalQty()) * 100) : 0;
  }

  getErrorMessage(err: any): string {
    if (typeof err.error === 'string') return err.error;
    return err.message || 'Unknown error occurred';
  }

  // --- Room Management ---
  addRoom() {
    if (!this.isManager) {
      this.toastService.show('🚫 Only Managers/Admins can create rooms', 'error');
      return;
    }
    const name = prompt('New Room Name:');
    if (!name || name.trim() === '') return;

    const cleanName = name.trim();
    if (this.rooms().includes(cleanName)) {
      this.toastService.show('Room already exists', 'warning');
      return;
    }

    const updatedRooms = [...this.rooms(), cleanName];
    this.updateEventRooms(updatedRooms);
  }

  deleteRoom(room: string) {
    if (!this.isManager) {
      this.toastService.show('🚫 Only Managers/Admins can delete rooms', 'error');
      return;
    }
    const count = this.getItemsInRoom(room).length;
    if (!confirm(`Delete room "${room}"? ${count} items will return to Unassigned.`)) return;

    this.isLoading.set(true);
    this.eventItemsService.deleteRoom(this.eventId, room).subscribe({
      next: () => {
        const updatedRooms = this.rooms().filter(r => r !== room);
        this.updateEventRooms(updatedRooms);
      },
      error: () => {
        this.toastService.show('Failed to delete room', 'error');
        this.isLoading.set(false);
      }
    });
  }

  private updateEventRooms(newRooms: string[]) {
    if (!this.eventData) return;

    const request = {
      ...this.eventData,
      ownerId: this.eventData.createdBy?.id || 15,
      customFields: {
        ...(this.eventData.customFields || {}),
        rooms: newRooms
      }
    };

    this.eventService.updateEvent(this.eventId, request).subscribe({
      next: () => {
        this.rooms.set(newRooms);
        this.toastService.show('Rooms updated', 'success');
        this.loadItems();
      },
      error: () => this.toastService.show('Failed to update event rooms', 'error')
    });
  }

  goBack() {
    this.router.navigate(['/inventory/event', this.eventId, 'cart']);
  }

  finalizeBooking() {
    if (!this.isManager) {
      this.toastService.show('🚫 Only Managers/Admins can finalize booking', 'error');
      return;
    }
    const unassignedCount = this.getUnassignedItems().length;
    if (unassignedCount > 0) {
      if (!confirm(`You still have ${unassignedCount} items unassigned. Proceed to Finalize?`)) return;
    }

    const currentUserId = 1; // Fallback
    this.isLoading.set(true);

    // Call backend to confirm items (Mark as BOOKED etc)
    this.eventItemsService.confirmEventItems(this.eventId, currentUserId).subscribe({
      next: () => {
        this.toastService.show('🚀 Booking Finalized Successfully!', 'success');
        // 🔹 NOW clear the cart from localStorage
        localStorage.removeItem('cart');

        setTimeout(() => {
          this.router.navigate(['/history/event', this.eventId], {
            queryParams: { returnUrl: this.router.url }
          });
        }, 1000);
      },
      error: (err: any) => {
        this.toastService.show('❌ Failed to finalize: ' + this.getErrorMessage(err), 'error');
        this.isLoading.set(false);
      }
    });
  }

  isExternal(item: any): boolean {
    if (!item) return false;
    const cat = item.categoryName || item.item?.category?.name || '';
    const name = (item.itemName || '').toLowerCase();
    const isRental = item.source === 'RENT_EXTERNAL' || item.status === 'PENDING_RENT' || item.status === 'RENTED';
    return isRental || cat === 'OTHER' || cat === 'External Rental' || name.includes('external');
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
    const brand = item.item?.brand || item.brand || '';
    const model = item.item?.model || item.model || '';
    const brandModel = `${brand} ${model}`.trim();

    if (this.isNote(item)) return item.itemName || '';

    // If we have brand/model, that's the primary title
    if (brandModel) return brandModel;

    // Fallback for external rentals or items without master stock info
    const name = item.itemName || '';
    const remark = item.remark || item.overbookNote || '';
    if (this.isExternal(item)) {
      if (remark.includes(' | ')) return remark.split(' | ')[0];
      if (remark && !remark.includes('Rental')) return remark;
    }
    return name;
  }

  getDescription(item: any) {
    if (this.isNote(item)) {
      return this.getNoteContent(item);
    }
    const remark = item.remark || item.overbookNote || '';
    if (this.isExternal(item) && remark.includes(' | ')) {
      return remark.split(' | ')[1];
    }

    const title = this.getTitle(item);
    const itemName = item.itemName || '';

    // If title is Brand/Model, and itemName is something else (like Category), show it as description
    if (title !== itemName && itemName && !title.includes(itemName)) {
      return itemName;
    }

    return item.item?.description || remark || '';
  }
}
