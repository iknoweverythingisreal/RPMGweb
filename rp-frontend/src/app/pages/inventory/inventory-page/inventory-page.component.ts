import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, NavigationStart } from '@angular/router';
import { UserService } from '../../../services/user.service';
import { ItemsService, Item } from '../../../services/Item.service';
import { EventService } from '../../../services/event.service';
import { ToastService } from '../../../services/toast.service';
import { Subscription, filter, firstValueFrom } from 'rxjs';
import { EventItemsService } from '../../../services/event-items.service';
import { environment } from 'src/environments/environment';


@Component({
  selector: 'app-inventory-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './inventory-page.component.html',
  styleUrls: ['./inventory-page.component.scss']
})
export class InventoryPageComponent implements OnInit, OnDestroy {
  items = signal<Item[]>([]);
  filteredItems = signal<Item[]>([]);
  selectedCategory = signal<string>('all');
  searchQuery = signal<string>('');
  selectedLocation = signal<string>('');
  currentLang = signal<'en' | 'th'>('en');
  eventId!: number;
  selectedEventId!: number;
  eventDates: { start: string, end: string } | null = null;
  availabilityMap = signal<Map<number, any>>(new Map());
  itemQuantities = signal<Map<number, number>>(new Map());
  bookedMap = signal<Map<number, number>>(new Map()); // ItemId -> BookedQty
  eventName = signal<string>('Equipment Inventory');
  cart = signal<any[]>([]); // Reactive cart state

  // Admin Item Management
  showCreateModal = false;
  isEditMode = false;
  editingItemId: number | null = null;
  newItem: Partial<Item> = {
    name: '',
    category: 'SOUND',
    totalQuantity: 1,
    availableQuantity: 1,
    location: 'warehouse-a',
    brand: '',
    model: '',
    uom: 'UNIT',
    serial: '',
    price: 0,
    status: 'AVAILABLE',
    remark: '',
    serialControl: false
  };

  // Rental Request
  showRentalModal = false;
  rentalRequest = {
    category: 'SOUND',
    brandModel: '',
    itemName: '',
    qty: 1,
    price: 0,
    note: ''
  };

  // Usage Information Modal
  showUsageModal = false;
  selectedUsageItem: any = null;

  private routerSub?: Subscription;

  categories = [
    { id: 'all', name: { en: 'All', th: 'ทั้งหมด' }, icon: '📦' },
    { id: 'SOUND', name: { en: 'Sound', th: 'เสียง' }, icon: '🔊' },
    { id: 'LED', name: { en: 'LED', th: 'LED' }, icon: '💡' },
    { id: 'VISUAL', name: { en: 'Visual', th: 'ภาพ' }, icon: '📺' },
    { id: 'LIGHTING', name: { en: 'Lighting', th: 'แสงสว่าง' }, icon: '💡' },
    { id: 'IT', name: { en: 'IT', th: 'ไอที' }, icon: '💻' }
  ];

  locations = [
    { value: '', label: { en: 'All Locations', th: 'ทุกสถานที่' } },
    { value: 'warehouse-a', label: { en: 'Warehouse A', th: 'คลังสินค้า A' } },
    { value: 'warehouse-b', label: { en: 'Warehouse B', th: 'คลังสินค้า B' } },
    { value: 'studio', label: { en: 'Studio', th: 'สตูดิโอ' } }
  ];

  uomOptions = [
    { value: 'UNIT', label: 'Unit' },
    { value: 'SET', label: 'Set' },
    { value: 'SQM', label: 'Sqm' },
    { value: 'ROLL', label: 'Roll' },
    { value: 'BOX', label: 'Box' }
  ];

  translations = {
    en: {
      title: 'Equipment Management',
      search: 'Search equipment...',
      location: 'Location',
      available: 'Available',
      limited: 'Limited',
      unavailable: 'Unavailable',
      viewDetails: 'View Details'
    },
    th: {
      title: 'จัดการอุปกรณ์',
      search: 'ค้นหาอุปกรณ์...',
      location: 'สถานที่',
      available: 'พร้อมใช้งาน',
      limited: 'จำกัด',
      unavailable: 'ไม่พร้อมใช้งาน',
      viewDetails: 'ดูรายละเอียด'
    }
  };

  constructor(
    private itemsService: ItemsService,
    private router: Router,
    private eventService: EventService,
    public userService: UserService,
    private route: ActivatedRoute,
    private toastService: ToastService,
    private eventItemsService: EventItemsService,
    private http: HttpClient
  ) { }

  ngOnInit() {
    this.eventId = Number(this.route.snapshot.paramMap.get('eventId'));

    // เก็บ eventId ไว้ให้หน้าอื่นใช้งาน เช่น item-detail-page, cart-page
    if (this.eventId) {
      this.selectedEventId = this.eventId;
      localStorage.setItem('selectedEventId', String(this.eventId));
    }

    this.loadItems();
    this.refreshCartSignal();

    // ⭐ AUTO-TRIGGER: Open rental modal if redirected from Cart
    this.route.queryParams.subscribe(params => {
      if (params['openRental'] === 'true') {
        setTimeout(() => this.openRentalModal(), 500); // Small delay to ensure view is ready
      }
    });
  }

  refreshCartSignal() {
    this.cart.set(this.getCurrentCart());
  }

  loadItems() {
    this.itemsService.getItems().subscribe(items => {
      // 1. Map variations (title/itemName/name) to a consistent 'name' field
      const consistentItems = items.map(item => ({
        ...item,
        name: (item.name || item.itemName || item.title || `Item #${item.id}`).trim()
      }));

      // 2. Group items by Name + Brand + Model
      const groupedMap = new Map<string, any>();

      consistentItems.forEach(item => {
        const key = `${item.brand || ''}|${item.model || ''}|${item.name}`.toLowerCase();
        if (!groupedMap.has(key)) {
          groupedMap.set(key, {
            ...item,
            totalQuantity: item.totalQuantity || 0,
            availableQuantity: item.availableQuantity || 0,
            originalItems: [item] // Store the actual records for booking logic
          });
        } else {
          const group = groupedMap.get(key);
          group.totalQuantity += (item.totalQuantity || 0);
          group.availableQuantity += (item.availableQuantity || 0);
          group.originalItems.push(item);
        }
      });

      // 3. Set aggregated items to the signal
      const aggregated = Array.from(groupedMap.values());
      this.items.set(aggregated);
      this.filterItems();

      if (this.eventId) {
        this.loadEventAndAvailability();
      }
    });
  }

  loadEventAndAvailability() {
    this.eventService.getEventById(this.eventId).subscribe(event => {
      if (event) {
        if (event.title) {
          this.eventName.set(event.title);
        }
        if (event.startDate && event.endDate) {
          this.eventDates = {
            start: event.startDate,
            end: event.endDate
          };
          this.fetchAvailabilityForAll();
          this.fetchBookedQuantities();
        }
      }
    });
  }

  fetchBookedQuantities() {
    this.eventItemsService.getEventItems(this.eventId).subscribe(items => {
      const bMap = new Map<number, number>();
      items.forEach(it => {
        const qty = Number(it.allocatedQuantity || 0);
        bMap.set(it.itemId, (bMap.get(it.itemId) || 0) + qty);
      });
      this.bookedMap.set(bMap);
    });
  }

  getGroupBookedQty(item: any): number {
    let sum = 0;
    (item.originalItems || []).forEach((original: any) => {
      sum += this.bookedMap().get(original.id) || 0;
    });
    return sum;
  }

  getRemainingQty(item: any): number {
    const avail = this.getAvailabilityForItem(item.id);
    if (!avail) return item.availableQuantity || 0;

    const inThisEvent = this.getGroupBookedQty(item);
    const inCart = this.getCartQtyForItem(item);

    // This is truly "remaining" for this specific event window
    return avail.available - inThisEvent - inCart;
  }

  isOverbooked(item: any): boolean {
    return this.getRemainingQty(item) < 0;
  }

  isShortageAfterAdd(item: any): boolean {
    const remaining = this.getRemainingQty(item);
    const adding = this.getQtyForItem(item.id);
    return (remaining - adding) < 0;
  }

  getOtherUsageQty(item: any): number {
    const avail = this.getAvailabilityForItem(item.id);
    if (!avail) return 0;
    // Difference between total stock and "available before this event"
    // is what's used by other overlapping events.
    return Math.max(0, avail.total - avail.available);
  }

  getCartQtyForItem(item: any): number {
    const cart = this.cart(); // Use signal
    let sum = 0;
    (item.originalItems || []).forEach((original: any) => {
      const match = cart.find((c: any) => c.itemId === original.id);
      if (match) sum += match.qty;
    });
    return sum;
  }

  fetchAvailabilityForAll() {
    if (!this.eventDates) return;
    const { start, end } = this.eventDates;

    // Pull all original item IDs from the aggregated list
    const allOriginalIds = this.items()
      .flatMap(group => (group.originalItems || []).map((it: any) => it.id));

    if (allOriginalIds.length === 0) return;

    this.itemsService.getBulkAvailability(allOriginalIds, this.eventId || 0, start, end).subscribe(results => {
      // 1. Create a map of ID -> Availability
      const idToAvail = new Map<number, any>();
      results.forEach(res => idToAvail.set(res.itemId, res));

      // 2. Aggregate availability back to the grouped items
      const groupAvailMap = new Map<number, any>();
      this.items().forEach(group => {
        let sumAvail = 0;
        let sumTotal = 0;
        let groupUsageDetails: any[] = [];

        (group.originalItems || []).forEach((it: any) => {
          const avail = idToAvail.get(it.id);
          if (avail) {
            sumAvail += avail.available;
            sumTotal += avail.total;
            if (avail.usageDetails) {
              groupUsageDetails.push(...avail.usageDetails);
            }
          } else {
            // Fallback to static if no dynamic availability found
            sumAvail += (it.availableQuantity || 0);
            sumTotal += (it.totalQuantity || 0);
          }
        });

        groupAvailMap.set(group.id, {
          available: sumAvail,
          total: sumTotal,
          usageDetails: groupUsageDetails
        });
      });

      this.availabilityMap.set(groupAvailMap);
    });
  }

  getUsageDetails(item: any): any[] {
    const avail = this.getAvailabilityForItem(item.id);
    return avail?.usageDetails || [];
  }

  openUsageModal(item: any) {
    this.selectedUsageItem = item;
    this.showUsageModal = true;
  }

  closeUsageModal() {
    this.showUsageModal = false;
    this.selectedUsageItem = null;
  }

  getQtyForItem(itemId: number): number {
    const q = this.itemQuantities().get(itemId);
    return q !== undefined ? q : 1;
  }

  canAddForItem(item: any): boolean {
    return true; // Always allow adding
  }

  incrementQty(itemId: number) {
    const map = new Map(this.itemQuantities());
    const current = map.get(itemId) || 1;
    map.set(itemId, current + 1);
    this.itemQuantities.set(map);
  }

  decrementQty(itemId: number) {
    const map = new Map(this.itemQuantities());
    const current = map.get(itemId) || 1;
    if (current > 1) {
      map.set(itemId, current - 1);
      this.itemQuantities.set(map);
    }
  }

  updateQty(itemId: number, value: string) {
    const qty = parseInt(value, 10);
    if (isNaN(qty) || qty < 0) {
      return;
    }
    const map = new Map(this.itemQuantities());
    map.set(itemId, qty);
    this.itemQuantities.set(map);
  }

  getAvailabilityForItem(itemId: number): any {
    return this.availabilityMap().get(itemId);
  }

  selectCategory(categoryId: string) {
    this.selectedCategory.set(categoryId);
    this.filterItems();
  }

  filterItems() {
    let filtered = this.items();

    if (this.selectedCategory() !== 'all') {
      filtered = filtered.filter(item =>
        item.category === this.selectedCategory()
      );
    }

    if (this.searchQuery()) {
      const q = this.searchQuery().toLowerCase();
      filtered = filtered.filter(item =>
        (item.name && item.name.toLowerCase().includes(q)) ||
        (item.brand && item.brand.toLowerCase().includes(q)) ||
        (item.model && item.model.toLowerCase().includes(q)) ||
        (item.itemName && item.itemName.toLowerCase().includes(q))
      );
    }

    if (this.selectedLocation()) {
      filtered = filtered.filter(item =>
        item.location === this.selectedLocation()
      );
    }

    this.filteredItems.set(filtered);
  }

  onSearchChange(query: string) {
    this.searchQuery.set(query);
    this.filterItems();
  }

  onLocationChange(location: string) {
    this.selectedLocation.set(location);
    this.filterItems();
  }

  toggleLanguage() {
    this.currentLang.set(this.currentLang() === 'en' ? 'th' : 'en');
  }

  viewItemDetail(itemId: number | string) {
    if (!this.eventId) {
      this.toastService.show('Please select an event first.', 'warning');
      this.router.navigate(['/inventory/select-event']);
      return;
    }

    // Unified route pattern: /inventory/event/:eventId/item/:itemId
    this.router.navigate(['/inventory/event', this.eventId, 'item', itemId]);
  }


  getStatusColor(item: any): string {
    const avail = this.getAvailabilityForItem(item.id);
    if (avail) {
      const ratio = avail.available / avail.total;
      if (ratio > 0.5) return '#10b981'; // green
      if (ratio > 0) return '#f59e0b'; // orange
      return '#ef4444'; // red
    }
    // Fallback to static data if availability not yet loaded
    const ratio = item.availableQuantity / item.totalQuantity;
    if (ratio > 0.5) return '#10b981';
    if (ratio > 0) return '#f59e0b';
    return '#ef4444';
  }

  getStatusText(item: any): string {
    const avail = this.getAvailabilityForItem(item.id);
    const lang = this.currentLang();
    if (avail) {
      const ratio = avail.available / avail.total;
      if (ratio > 0.5) return this.translations[lang].available;
      if (ratio > 0) return this.translations[lang].limited;
      return this.translations[lang].unavailable;
    }
    const ratio = item.availableQuantity / item.totalQuantity;
    if (ratio > 0.5) return this.translations[lang].available;
    if (ratio > 0) return this.translations[lang].limited;
    return this.translations[lang].unavailable;
  }

  t(key: keyof typeof this.translations.en): string {
    return this.translations[this.currentLang()][key];
  }

  getCategoryName(categoryId: string): string {
    const category = this.categories.find(c => c.id === categoryId);
    return category ? category.name[this.currentLang()] : categoryId;
  }

  getCategoryIcon(categoryId: string): string {
    const category = this.categories.find(c => c.id === categoryId);
    return category ? category.icon : '📦';
  }

  getLocationName(value: string): string {
    const location = this.locations.find(l => l.value === value);
    return location ? location.label[this.currentLang()] : value;
  }

  markAsPrepared() {
    const userId = Number(localStorage.getItem('userId'));
    this.eventService.markPrepared(this.eventId, userId).subscribe({
      next: () => {
        this.toastService.show('✅ Items marked as READY.', 'success');
        this.loadItems();
      },
      error: (err) =>
        this.toastService.show('❌ Failed: ' + (err?.error?.message || err.message), 'error'),
    });
  }

  markAsChecked() {
    const userId = Number(localStorage.getItem('userId'));
    this.eventService.markChecked(this.eventId, userId).subscribe({
      next: () => {
        this.toastService.show('📦 Items marked as CHECKED.', 'success');
        this.loadItems();
      },
      error: (err) =>
        this.toastService.show('❌ Failed: ' + (err?.error?.message || err.message), 'error'),
    });
  }

  get userRole(): string {
    return this.userService.currentUser?.role ?? 'VISITOR';
  }

  get isManager(): boolean {
    return this.userService.isManager;
  }

  get isAdmin(): boolean {
    return this.userService.isAdmin;
  }

  get isTech(): boolean {
    return this.userService.isTechnical;
  }

  // 🔹 อ่าน cart จาก localStorage
  private getCurrentCart(): any[] {
    const raw = localStorage.getItem('cart');
    if (!raw) return [];
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }

  // 🔹 Save cart กลับเข้า localStorage
  private saveCart(cart: any[]) {
    localStorage.setItem('cart', JSON.stringify(cart));
    this.cart.set(cart); // Update signal for real-time UI
  }

  // 🔹 กดเพิ่มของเข้า cart
  addToCart(item: any) {
    if (!this.isManager) {
      this.toastService.show('🚫 Only Manager/Admin can add items to cart.', 'error');
      return;
    }

    if (this.isPastEvent()) {
      this.toastService.show('Event จบไปแล้ว ไม่สามารถเพิ่มอุปกรณ์ได้', 'error');
      return;
    }

    const requestedQty = this.getQtyForItem(item.id);
    if (requestedQty <= 0) return;

    // Allow overbooking - removed availability check here

    // 🚀 Distribution Logic: Distribute requestedQty across originalItems
    // For Overbooking, we simply add to the first/most relevant item if unavailable
    // OR we distribute as much as possible to available ones, then dump the rest to the last one (or logic TBD)
    // Detailed logic:
    // 1. Try to fill from available slots in originalItems
    // 2. If shortage, still add to cart (likely on the first or last originalItem) regarding it as 'overbooked'

    const cart = this.getCurrentCart();
    let leftToAdd = requestedQty;

    const availMap = this.availabilityMap();
    const originals = item.originalItems || [];

    if (originals.length === 0) {
      // Single item case logic would go here if needed, but assuming originals always populated for aggregated view
    }

    // First pass: Fill available
    for (const original of originals) {
      if (leftToAdd <= 0) break;

      const inCart = cart
        .filter((c: any) => c.itemId === original.id)
        .reduce((sum: number, c: any) => sum + c.qty, 0);

      const realTimeAvail = availMap.get(original.id);
      const inThisEvent = this.bookedMap().get(original.id) || 0;
      const trulyAvail = Math.max(0, (realTimeAvail?.available || 0) - inThisEvent);

      const canTake = Math.min(leftToAdd, Math.max(0, trulyAvail - inCart));

      if (canTake > 0) {
        this.pushToCart(cart, original, canTake);
        leftToAdd -= canTake;
      }
    }

    // Second pass: If still leftToAdd, force add to first item (Overbooking)
    if (leftToAdd > 0 && originals.length > 0) {
      const target = originals[0]; // Dump excess here or find best fit? 
      // For now, dump on first item. Cart page calculation will show it as overbooked.
      this.pushToCart(cart, target, leftToAdd);
      leftToAdd = 0;
    }

    this.toastService.show(`✅ Added ${requestedQty} ${item.name} to cart`, 'success');
    this.saveCart(cart);

    // Reset quantity
    const resetMap = new Map(this.itemQuantities());
    resetMap.set(item.id, 1);
    this.itemQuantities.set(resetMap);
  }

  private pushToCart(cart: any[], item: any, qty: number) {
    const existing = cart.find((c: any) => c.itemId === item.id);
    if (existing) {
      existing.qty += qty;
    } else {
      cart.push({
        itemId: item.id,
        itemName: item.name,
        category: item.category,
        uom: item.uom,
        qty: qty,
        unitPrice: 0,
        brand: item.brand,
        model: item.model,
      });
    }
  }

  // 🔹 กดจองล่วงหน้า (Reserve/Pre-order)
  reserveToCart(item: any) {
    if (!this.isManager) {
      this.toastService.show('🚫 Only Manager/Admin can reserve items.', 'error');
      return;
    }

    if (this.isPastEvent()) {
      this.toastService.show('Event จบไปแล้ว ไม่สามารถจองอุปกรณ์ได้', 'error');
      return;
    }

    const requestedQty = this.getQtyForItem(item.id);
    const cart = this.getCurrentCart();
    let remaining = requestedQty;

    // ระบบ Reserve จะไม่เช็ค Availability (หรือเช็คแต่ยอมให้จองเป็น REQUESTED)
    for (const original of (item.originalItems || [])) {
      if (remaining <= 0) break;

      // สำหรับ Reserve เราจะจองตัวที่ว่างก่อน ถ้าไม่พอค่อยจองตัวที่ไม่ว่าง
      const existing = cart.find((c: any) => c.itemId === original.id);
      const canTake = remaining; // ในโหมด Reserve เราเอาเท่าที่ต้องการเลย

      if (existing) {
        existing.qty += canTake;
        existing.status = 'CONFIRMED';
        existing.autoApprove = true;
      } else {
        cart.push({
          itemId: original.id,
          itemName: original.name,
          category: original.category,
          uom: original.uom,
          qty: canTake,
          unitPrice: 0,
          brand: original.brand,
          model: original.model,
          status: 'CONFIRMED',
          autoApprove: true
        });
      }
      remaining -= canTake;
    }

    this.saveCart(cart);
    this.toastService.show(`📅 จดจองล่วงหน้าสำหรับ ${requestedQty} ${item.name} เรียบร้อย`, 'success');

    // Reset quantity back to 1
    const resetMap = new Map(this.itemQuantities()); // Changed name to avoid confusion with Map class
    resetMap.set(item.id, 1);
    this.itemQuantities.set(resetMap);
  }

  // 🔹 ไปหน้า Room Assignment (Step ใหม่)
  goToCart() {
    if (!this.eventId) {
      console.error('[INVENTORY] No eventId - cannot navigate to cart');
      this.toastService.show('กรุณาเลือก Event ก่อน', 'warning');
      this.router.navigate(['/inventory/select-event']);
      return;
    }
    this.router.navigate(['/inventory/event', this.eventId, 'cart']);
  }

  // 🔹 ใช้โชว์จำนวน item ใน cart
  get cartCount(): number {
    const cart = this.cart();
    const uniqueKeys = new Set(cart.map(c => `${c.brand || ''}|${c.model || ''}|${c.itemName}`.toLowerCase()));
    return uniqueKeys.size;
  }

  // 🔹 ย้อนกลับไปหน้า Event Detail
  goBack() {
    if (this.eventId) {
      this.router.navigate(['/inventory/select-event']);
    } else {
      this.router.navigate(['/calendar']);
    }
  }

  goToCalendar() {
    this.router.navigate(['/calendar']);
  }

  // ===== Admin Item Management =====

  openCreateModal(item?: Item) {
    if (item) {
      this.isEditMode = true;
      this.editingItemId = item.id;
      this.newItem = {
        ...item,
        name: item.name || item.itemName || ''
      };
    } else {
      this.isEditMode = false;
      this.editingItemId = null;
      this.newItem = {
        name: '',
        category: 'SOUND',
        totalQuantity: 1,
        availableQuantity: 1,
        location: 'warehouse-a',
        brand: '',
        model: '',
        uom: 'UNIT',
        serial: '',
        price: 0,
        status: 'AVAILABLE',
        remark: '',
        serialControl: false
      };
    }
    this.showCreateModal = true;
  }

  closeCreateModal() {
    this.showCreateModal = false;
    this.newItem = {
      name: '',
      category: 'SOUND',
      totalQuantity: 1,
      availableQuantity: 1,
      location: 'warehouse-a',
      brand: '',
      model: '',
      uom: 'UNIT',
      serial: '',
      price: 0,
      status: 'AVAILABLE',
      remark: '',
      serialControl: false
    };
  }

  createItem() {
    if (!this.newItem.name) {
      this.toastService.show('⚠️ Name is required.', 'warning');
      return;
    }

    // ⭐ If editing, update instead of create
    if (this.isEditMode && this.editingItemId) {
      this.http.put<Item>(`${environment.apiUrl}/api/items/${this.editingItemId}`, this.newItem).subscribe({
        next: () => {
          this.toastService.show('✅ Master Stock updated successfully!', 'success');
          this.closeCreateModal();
          this.loadItems();
        },
        error: (err: any) => this.toastService.show('❌ Failed to update: ' + (err?.error?.message || err.message), 'error')
      });
      return;
    }

    if (!this.newItem.brand || !this.newItem.model) {
      this.toastService.show('⚠️ Brand and Model are mandatory fields.', 'warning');
      return;
    }

    // Alignment: Name is also description if description is empty
    if (!this.newItem.description) {
      this.newItem.description = this.newItem.name;
    }

    // ⭐ AUTO: If serial is entered, enable serial tracking automatically
    this.newItem.serialControl = !!(this.newItem.serial && this.newItem.serial.trim());

    // Ensure available = total initially
    this.newItem.availableQuantity = this.newItem.totalQuantity;

    this.itemsService.createItem(this.newItem).subscribe({
      next: () => {
        this.toastService.show('✅ Item created successfully!', 'success');
        this.closeCreateModal();
        this.loadItems();
      },
      error: (err) => this.toastService.show('❌ Failed: ' + (err?.error?.message || err.message), 'error')
    });
  }

  deleteItem(item: Item) {
    if (!confirm(`Are you sure you want to delete "${item.name}"? This cannot be undone.`)) {
      return;
    }

    this.itemsService.deleteItem(item.id).subscribe({
      next: () => {
        this.toastService.show('🗑 Item deleted', 'success');
        this.loadItems();
      },
      error: (err) => {
        this.toastService.show('❌ Failed to delete item. It may be in use.', 'error');
        console.error(err);
      }
    });
  }

  // ===== Rental Request Logic =====

  openRentalModal() {
    if (!this.eventId) {
      this.toastService.show('Please select an event first.', 'warning');
      return;
    }
    this.showRentalModal = true;
  }

  closeRentalModal() {
    this.showRentalModal = false;
    this.rentalRequest = {
      category: 'SOUND',
      brandModel: '',
      itemName: '',
      qty: 1,
      price: 0,
      note: ''
    };
  }

  /**
   * Check if event is in the past
   */
  isPastEvent(): boolean {
    if (!this.eventDates) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const end = new Date(this.eventDates.end);
    end.setHours(0, 0, 0, 0);
    return end < today;
  }

  async submitRentalRequest() {
    if (this.isPastEvent()) {
      this.toastService.show('Event จบไปแล้ว ไม่สามารถส่งคำขอเช่าได้', 'error');
      return;
    }
    // Validate required fields
    if (!this.rentalRequest.itemName || !this.rentalRequest.qty) {
      this.toastService.show('Please fill in Item Name and Quantity', 'error');
      return;
    }

    // Validate eventId
    if (!this.eventId || this.eventId === 0) {
      this.toastService.show('No event selected. Please select an event first.', 'error');
      return;
    }

    try {
      // 1. Find the Service Item ID for the selected category
      console.log('Fetching service items for category:', this.rentalRequest.category);

      let serviceItems: Item[] = [];
      try {
        serviceItems = await firstValueFrom(this.itemsService.getServiceItems());
        console.log('Service items fetched:', serviceItems);
      } catch (fetchError) {
        console.error('Failed to fetch service items:', fetchError);
        this.toastService.show('Failed to fetch service items. Please try again or contact Admin.', 'error');
        return;
      }

      if (!serviceItems || serviceItems.length === 0) {
        this.toastService.show('No service items found. Please contact Admin to set up External Rental items.', 'error');
        return;
      }

      const targetItem = serviceItems.find(i =>
        i.category?.toUpperCase() === this.rentalRequest.category?.toUpperCase()
      );

      if (!targetItem) {
        console.error(`No service item found for category: ${this.rentalRequest.category}`);
        console.log('Available service items:', serviceItems.map(i => ({ id: i.id, name: i.name, category: i.category })));
        this.toastService.show(
          `Service Item for ${this.rentalRequest.category} not found. Please contact Admin to create it.`,
          'error'
        );
        return;
      }

      console.log('Target service item found:', targetItem);

      // 2. Prepare the request payload
      const combinedName = `[${this.rentalRequest.brandModel || 'N/A'}] ${this.rentalRequest.itemName || 'Unnamed Item'}`;
      const payload = [{
        itemId: targetItem.id,
        requestedQuantity: this.rentalRequest.qty,
        unitPrice: this.rentalRequest.price || 0,
        rateType: 'fixed' as const, // Default to fixed for rentals
        remark: this.rentalRequest.note || '', // Standard remark
        overbookNote: `${combinedName} (Rental Request) - ${this.rentalRequest.note || 'No additional notes'}`, // Special note for Overbooking
        qty: this.rentalRequest.qty
      }];

      console.log('Submitting rental request payload:', payload);

      // 3. Send to Backend
      this.eventItemsService.addBulkItemsToEvent(this.eventId, payload).subscribe({
        next: () => {
          console.log('Rental request submitted successfully');
          this.toastService.show('✅ Rental Request Submitted', 'success');
          this.closeRentalModal();
          this.loadItems(); // Refresh to show availability changes if any
        },
        error: (err) => {
          console.error('Failed to submit rental request:', err);
          const errorMsg = err?.error?.message || err?.message || 'Unknown error';
          this.toastService.show(`❌ Failed to submit request: ${errorMsg}`, 'error');
        }
      });

    } catch (err: any) {
      console.error('Error processing rental request:', err);
      const errorMsg = err?.message || 'Unknown error';
      this.toastService.show(`❌ Error processing request: ${errorMsg}`, 'error');
    }
  }

  ngOnDestroy() {
    if (this.routerSub) {
      this.routerSub.unsubscribe();
    }
  }
}