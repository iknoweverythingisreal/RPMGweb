import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EventService } from '../../../services/event.service';
import { EventItemsService } from '../../../services/event-items.service';
import { ItemsService } from '../../../services/Item.service';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../services/toast.service';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';

@Component({
    selector: 'app-history-detail-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './history-detail-page.component.html',
    styleUrls: ['./history-detail-page.component.scss']
})
export class HistoryDetailPageComponent implements OnInit {
    eventId!: number;
    event: any = null;
    eventItems: any[] = [];
    historyLogs: any[] = [];
    isLoading = false;
    editingItemId: number | null = null;
    editQty: number = 0;
    selectedCategory: string = 'ALL';
    showSubstitutionHistory: boolean = false;
    shouldAutoPrint: boolean = false;
    availabilityMap = signal<Map<number, any>>(new Map()); // Key is itemId
    returnUrl: string | null = null;

    get filteredItems() {
        if (this.selectedCategory === 'ALL') return this.eventItems;
        return this.eventItems.filter(it => {
            const cat = it.item?.category || it.category || '';
            return cat.toUpperCase() === this.selectedCategory.toUpperCase();
        });
    }

    get warehouseItems() {
        // Warehouse items are strictly items with allocatedQuantity > 0 
        // AND that allocated amount is the full requested amount (if not, it's a shortage).
        // Actually, for simplicity in "Warehouse", we show what we HAVE from stock.
        return this.filteredItems.filter(it => Number(it.allocatedQuantity || 0) > 0);
    }

    get rentalItems() {
        // Rental items are: 
        // 1. Items with 0 allocated OR
        // 2. Items where requested > allocated (The missing part is a shortage/rental)
        return this.filteredItems.filter(it => {
            const requested = Number(it.requestedQuantity || 0);
            const allocated = Number(it.allocatedQuantity || 0);
            const isRentalSource = it.source === 'RENT_EXTERNAL' || it.status === 'PENDING_RENT' || it.status === 'RENTED';
            return isRentalSource || allocated === 0 || requested > allocated;
        });
    }

    get warehouseGrouped() {
        return this.groupItemsByRoom(this.warehouseItems);
    }

    get rentalGrouped() {
        return this.groupItemsByRoom(this.rentalItems);
    }

    get totalItemsCount(): number {
        return this.eventItems.reduce((acc, it) => acc + (Number(it.requestedQuantity || it.allocatedQuantity || 0)), 0);
    }

    private groupItemsByRoom(items: any[]) {
        const groups = new Map<string, any[]>();

        items.forEach(it => {
            const roomName = it.room || 'Unassigned';
            if (!groups.has(roomName)) {
                groups.set(roomName, []);
            }
            groups.get(roomName)?.push(it);
        });

        return Array.from(groups.entries()).map(([roomName, items]) => {
            return { roomName, items };
        }).sort((a, b) => {
            if (a.roomName === 'Unassigned') return 1;
            if (b.roomName === 'Unassigned') return -1;
            return a.roomName.localeCompare(b.roomName);
        });
    }

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private eventService: EventService,
        private eventItemsService: EventItemsService,
        private itemsService: ItemsService,
        public userService: UserService,
        private toastService: ToastService,
        private http: HttpClient
    ) { }

    ngOnInit() {
        const id = this.route.snapshot.paramMap.get('eventId');
        if (!id) {
            this.toastService.show('Invalid event ID', 'error');
            this.router.navigate(['/history']);
            return;
        }
        this.eventId = Number(id);

        this.route.queryParams.subscribe(params => {
            if (params['autoPrint'] === 'true') {
                this.shouldAutoPrint = true;
            }
            if (params['returnUrl']) {
                this.returnUrl = params['returnUrl'];
            }
        });

        this.loadEventData();
    }

    loadEventData() {
        this.isLoading = true;

        // Load event details
        this.eventService.getEventById(this.eventId).subscribe({
            next: (event) => {
                this.event = event;
                this.fetchAvailability();
            },
            error: (err) => {
                console.error('Failed to load event:', err);
                this.toastService.show('Failed to load event details', 'error');
            }
        });

        // Load event items
        this.eventItemsService.getEventItems(this.eventId).subscribe({
            next: (items) => {
                this.eventItems = items;
                this.isLoading = false;
                this.fetchAvailability(); // Fetch availability after items are loaded

                if (this.shouldAutoPrint) {
                    this.triggerAutoPrint();
                }
            },
            error: (err) => {
                console.error('Failed to load event items:', err);
                this.isLoading = false;
            }
        });

        // Load history logs
        this.loadHistoryLogs();
    }

    private triggerAutoPrint() {
        // Reset the flag to avoid repeated prints on re-renders if any
        this.shouldAutoPrint = false;

        // Use a slight delay to ensure Angular has rendered the data in the DOM
        setTimeout(() => {
            this.exportToPDF();
        }, 1000);
    }

    loadHistoryLogs() {
        this.http.get<any[]>(`${environment.apiUrl}/api/event-history/event/${this.eventId}`).subscribe({
            next: (logs) => {
                this.historyLogs = (logs || []).sort((a, b) => {
                    const tA = new Date(a?.changedAt || a?.changed_at || a?.createdAt || a?.created_at || 0).getTime();
                    const tB = new Date(b?.changedAt || b?.changed_at || b?.createdAt || b?.created_at || 0).getTime();
                    return tB - tA;
                });
            },
            error: (err) => {
                console.error('Failed to load history logs:', err);
            }
        });
    }

    // canEdit is now handled by a helper method or directly in template
    checkCanEdit(user: any): boolean {
        const role = user?.role;
        return role === 'ADMIN' || role === 'MANAGER';
    }

    startEditQty(item: any) {
        this.editingItemId = item.id;
        this.editQty = item.requestedQuantity || item.allocatedQuantity || 0;
    }

    cancelEdit() {
        this.editingItemId = null;
        this.editQty = 0;
    }

    saveQty(item: any) {
        if (!this.editQty || this.editQty <= 0) {
            this.toastService.show('Quantity must be greater than 0', 'error');
            return;
        }

        this.http.put(`${environment.apiUrl}/api/event-items/${item.id}/quantity`, { quantity: this.editQty }, { responseType: 'text' }).subscribe({
            next: () => {
                this.toastService.show('Quantity updated successfully', 'success');
                this.editingItemId = null;
                this.loadEventData(); // Reload to get updated data and new history log
            },
            error: (err) => {
                console.error('Failed to update quantity:', err);
                this.toastService.show('Failed to update quantity: ' + (err?.error?.message || err.message), 'error');
            }
        });
    }

    deleteItem(item: any) {
        if (!confirm(`Are you sure you want to remove ${item.item?.name || item.itemName}?`)) return;

        this.http.delete<any>(`${environment.apiUrl}/api/event-items/${item.id}`).subscribe({
            next: (res) => {
                this.toastService.show('Item moved back to cart', 'success');

                // 🔹 Restore to Local Cart
                const raw = localStorage.getItem('cart');
                const cart = raw ? JSON.parse(raw) : [];

                cart.push({
                    itemId: res.itemId,
                    itemName: res.itemName,
                    brand: res.brand,
                    model: res.model,
                    category: res.category,
                    uom: res.uom,
                    qty: Number(res.requestedQuantity || 0),
                    unitPrice: res.unitPrice || 0,
                    status: res.status, // preserve status if it was rental
                    source: res.source  // preserve source (RENT_EXTERNAL)
                });

                localStorage.setItem('cart', JSON.stringify(cart));

                // 🔹 Navigate to Cart
                this.router.navigate(['/inventory/event', this.eventId, 'cart']);
            },
            error: (err) => {
                console.error('Failed to remove item:', err);
                this.toastService.show('Failed to remove item', 'error');
            }
        });
    }

    addMoreItems() {
        this.router.navigate(['/inventory/event', this.eventId]);
    }

    fetchAvailability() {
        if (!this.event) {
            console.log('[HISTORY-DETAIL] fetchAvailability: event not loaded yet');
            return;
        }
        if (!this.eventItems || this.eventItems.length === 0) {
            console.log('[HISTORY-DETAIL] fetchAvailability: eventItems empty or not loaded');
            return;
        }

        const startRaw = this.event.startDate || this.event.start_date;
        const endRaw = this.event.endDate || this.event.end_date;

        if (!startRaw || !endRaw) {
            console.warn('[HISTORY-DETAIL] Missing dates for availability:', { startRaw, endRaw });
            this.handleAvailabilityFallback('missing dates');
            return;
        }

        const start = startRaw.toString().includes('T') ? startRaw.toString().split('T')[0] : startRaw.toString();
        const end = endRaw.toString().includes('T') ? endRaw.toString().split('T')[0] : endRaw.toString();

        const itemIds = Array.from(new Set(this.eventItems.map(it => Number(it.itemId)).filter(id => !isNaN(id))));

        console.log(`[HISTORY-DETAIL] Fetching availability for ${itemIds.length} items (${start} to ${end})`);

        if (itemIds.length === 0) {
            this.handleAvailabilityFallback('no valid item IDs');
            return;
        }

        this.itemsService.getBulkAvailability(itemIds, 0, start, end).subscribe({
            next: (results) => {
                console.log('[HISTORY-DETAIL] Bulk availability received, count:', results?.length);
                const bulkResults = new Map<number, any>();
                if (results && Array.isArray(results)) {
                    results.forEach(res => {
                        const id = Number(res.itemId || res.id);
                        if (!isNaN(id)) bulkResults.set(id, res);
                    });
                }

                const newAvailMap = new Map<number, any>();
                console.log(`[HISTORY-DETAIL] Mapping ${this.eventItems.length} items to bulk results...`);

                this.eventItems.forEach(it => {
                    const iid = Number(it.itemId);
                    const eid = Number(it.id);
                    const avail = bulkResults.get(iid);

                    if (avail) {
                        const maxStock = Number(avail.available) ?? 0;
                        const totalStock = Number(avail.total) ?? Number(avail.totalQuantity) ?? 0;
                        const requested = Number(it.requestedQuantity || it.allocatedQuantity || it.quantity || 0);

                        // Rental items (allocatedQuantity === 0) don't use warehouse stock
                        // so they should NEVER count as overbooked
                        const isRentalItem = Number(it.allocatedQuantity || 0) === 0;
                        const overbooked = isRentalItem ? 0 : Math.max(0, requested - maxStock);

                        newAvailMap.set(eid, {
                            available: maxStock,
                            total: totalStock,
                            overbooked: overbooked,
                            netAvail: isRentalItem ? 0 : maxStock - requested
                        });
                    } else {
                        // If no specific bulk result, still set an entry so UI doesn't break
                        newAvailMap.set(eid, {
                            available: 0,
                            total: 0,
                            overbooked: 0,
                            netAvail: 0
                        });
                    }
                });

                console.log('[HISTORY-DETAIL] Final availabilityMap size:', newAvailMap.size);
                this.availabilityMap.set(newAvailMap);
            },
            error: (err) => {
                console.error('[HISTORY-DETAIL] API Error:', err);
                this.handleAvailabilityFallback('API error');
            }
        });
    }

    private handleAvailabilityFallback(reason: string) {
        console.log('[HISTORY-DETAIL] Fallback triggered:', reason);
        const fbMap = new Map<number, any>();
        this.eventItems.forEach(it => {
            fbMap.set(Number(it.id), {
                available: 0,
                total: 0,
                overbooked: 0,
                netAvail: 0
            });
        });
        this.availabilityMap.set(fbMap);
    }

    goBack() {
        if (this.returnUrl) {
            this.router.navigateByUrl(this.returnUrl);
        } else {
            // ⭐ FALLBACK: If we are in the history path, go back to history list
            if (this.router.url.includes('/history/')) {
                this.router.navigate(['/history']);
            } else {
                this.router.navigate(['/inventory/event', this.eventId, 'room-assign']);
            }
        }
    }

    goToCalendar() {
        this.router.navigate(['/calendar']);
    }

    isPastEvent(): boolean {
        if (!this.event || !this.event.endDate) return false;
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const end = new Date(this.event.endDate);
        end.setHours(0, 0, 0, 0);
        return end < today;
    }

    formatDate(dateStr: string): string {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        return date.toLocaleString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    getActionLabel(action: string): string {
        const labels: any = {
            'EVENT_CREATED': 'Event Created',
            'ITEMS_BOOKED': 'Items Booked',
            'ITEM_QTY_UPDATED': 'Quantity Updated',
            'ITEM_ADDED': 'Item Added',
            'ITEM_REMOVED': 'Item Removed',
            'ITEM_SUBSTITUTION': 'Equipment Substitution',
            'PREPARE': 'Items Ready',
            'CHECK': 'Items Checked',
            'CONFIRM': 'Manager Confirmed',
            'RENT_REQUEST': 'External Rent Request',
            'RENT_APPROVED': 'External Rent Approved',
            'RENT_REJECTED': 'External Rent Rejected',
            'RETURN_REQUEST': 'Return Requested',
            'RETURN_APPROVED': 'Return Approved'
        };
        return labels[action] || action;
    }

    getActionIcon(action: string): string {
        const icons: any = {
            'EVENT_CREATED': 'fa-calendar-plus',
            'ITEMS_BOOKED': 'fa-box',
            'ITEM_QTY_UPDATED': 'fa-edit',
            'ITEM_ADDED': 'fa-plus-circle',
            'ITEM_REMOVED': 'fa-minus-circle',
            'ITEM_SUBSTITUTION': 'fa-exchange-alt',
            'PREPARE': 'fa-check-double',
            'CHECK': 'fa-clipboard-check',
            'CONFIRM': 'fa-user-check',
            'RENT_REQUEST': 'fa-truck-loading',
            'RENT_APPROVED': 'fa-check-square',
            'RENT_REJECTED': 'fa-window-close',
            'RETURN_REQUEST': 'fa-undo',
            'RETURN_APPROVED': 'fa-warehouse'
        };
        return icons[action] || 'fa-info-circle';
    }

    exportToPDF() {
        console.log('[HISTORY-DETAIL] Exporting to PDF (window.print)...');
        window.print();
    }
}
