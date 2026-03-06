import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { EventService } from '../../../services/event.service';
import { UserService } from '../../../services/user.service';
import { ItemsService } from '../../../services/Item.service';
import { ToastService } from '../../../services/toast.service';

@Component({
    selector: 'app-replace-items-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './replace-items-page.component.html',
    styleUrls: ['./replace-items-page.component.scss']
})
export class ReplaceItemsPageComponent implements OnInit {
    // Data
    items: any[] = [];
    filteredItems: any[] = [];
    groupedItems: any[] = []; // Grouped items by brand+model with summed quantities

    // Search
    searchQuery: string = '';

    // Categories for grouping - will be populated from data
    categories: string[] = [];

    // Modal State
    selectedItem: any = null; // The item causing the action (Source)
    showModal: boolean = false;

    // Modal Form
    actionType: 'REPLACE_WITH' | 'MOVE_TO_STORE' = 'REPLACE_WITH';
    targetType: 'STORAGE' | 'EVENT' = 'STORAGE';

    // Selection
    targetEventId: number | null = null;
    targetItemId: number | null = null;

    // lists for dropdowns
    activeEvents: any[] = [];
    candidateItems: any[] = [];
    filteredCandidateItems: any[] = [];
    equipmentSearchQuery: string = '';

    // meta
    reason: string = '';
    targetEventItemId: number | null = null;
    swapQuantity: number = 1;
    swapMode: 'MUTUAL' | 'ONE_WAY' = 'MUTUAL';
    currentUser: any = null;

    protected readonly Math = Math;
    // Wizard State
    currentStep: number = 1;

    availabilityMap = signal<Map<number, any>>(new Map());

    constructor(
        private http: HttpClient,
        private eventService: EventService,
        private userService: UserService,
        private itemsService: ItemsService,
        private router: Router,
        private toastService: ToastService
    ) {
        this.currentUser = this.userService.currentUser;
    }

    ngOnInit() {
        this.loadData();
    }

    nextStep() {
        if (this.currentStep < 4) {
            // If returning to store, skip step 2 and 3
            if (this.currentStep === 1 && this.actionType === 'MOVE_TO_STORE') {
                this.currentStep = 4;
            } else if (this.currentStep === 25) {
                // Special case: 25 (Select Event) -> 3 (Select Item)
                this.currentStep = 3;
            } else {
                this.currentStep++;
                if (this.currentStep === 2) {
                    this.updateCandidates();
                }
            }
        }
        console.log('nextStep called, currentStep now:', this.currentStep);
    }

    prevStep() {
        if (this.currentStep > 1) {
            // Special case: From step 25 (Select Event) -> back to step 2 (Replacement Source)
            if (this.currentStep === 25) {
                this.currentStep = 2;
            }
            // If returning to store, jump back from 4 to 1
            else if (this.currentStep === 4 && this.actionType === 'MOVE_TO_STORE') {
                this.currentStep = 1;
            }
            // If at step 3 and came from EVENT selection, go back to step 25
            else if (this.currentStep === 3 && this.targetType === 'EVENT') {
                this.currentStep = 25;
            }
            // Normal case: just go back one step
            else {
                this.currentStep--;
            }
        }

    }

    resetModalForm() {
        this.actionType = 'REPLACE_WITH';
        this.targetType = 'STORAGE';
        this.targetEventId = null;
        this.targetItemId = null;
        this.reason = '';
        this.candidateItems = [];
        this.currentStep = 1;
    }

    loadData() {
        // Load All Event Items from event_items table
        this.http.get<any[]>('/api/event-items').subscribe({
            next: (res) => {
                console.log('Loaded event items:', res);
                // Filter logic: Exclude CANCELLED/RETURNED. Show if date is active OR invalid (fail-open).
                const today = new Date();
                today.setHours(0, 0, 0, 0);

                this.items = res.filter(i => {
                    const isStatusOk = !['RETURNED', 'CANCELLED', 'REJECTED'].includes(i.status);

                    // Date Check: If end date exists and is valid, check if it's in the past.
                    // If invalid or missing, Default to SHOW (fail-open).
                    let isDateOk = true;
                    if (i.eventEndDate) {
                        const endDate = new Date(i.eventEndDate);
                        if (!isNaN(endDate.getTime())) {
                            isDateOk = endDate >= today;
                        }
                    }

                    return isStatusOk && isDateOk;
                });

                console.log(`Loaded ${res.length} items. Showing ${this.items.length} after filtering.`);
                // Extract unique categories from the data
                const uniqueCategories = new Set(this.items.map(item => item.category).filter(c => c));
                this.categories = Array.from(uniqueCategories).sort();
                console.log('Categories found:', this.categories);
                this.groupItems();
                this.filterItems();
            },
            error: (err) => {
                console.error('Failed to load event items:', err);
                this.toastService.show('Failed to load items: ' + (err.error?.message || err.message), 'error');
            }
        });

        // Load Active Events (for dropdown)
        this.eventService.getEvents().subscribe(events => {
            const today = new Date();
            today.setHours(0, 0, 0, 0);
            this.activeEvents = events.filter(e => new Date(e.endDate) >= today);
        });
    }

    groupItems() {
        // Group items by brand + model + category (ignoring eventId to consolidate timeline)
        const grouped = new Map<string, any>();

        this.items.forEach(item => {
            const key = `${item.brand || ''}_${item.model || ''}_${item.category}`.trim().toLowerCase();

            if (grouped.has(key)) {
                grouped.get(key).allBookings.push(item);
            } else {
                grouped.set(key, {
                    brand: item.brand,
                    model: item.model,
                    category: item.category,
                    itemName: item.itemName,
                    allBookings: [item],
                    isGrouped: true
                });
            }
        });

        this.groupedItems = Array.from(grouped.values()).map(group => {
            // Sort all bookings for this item chronologically by start date
            const sorted = group.allBookings.sort((a: any, b: any) =>
                new Date(a.eventStartDate).getTime() - new Date(b.eventStartDate).getTime()
            );

            // Assign "Now" (the earliest/current) and "Next" (the following one)
            group.nowEvent = sorted[0] || null;
            group.nextEvent = sorted[1] || null;

            // Carry over meta-info from the "Now" event for general display
            if (group.nowEvent) {
                group.id = group.nowEvent.id;
                group.itemId = group.nowEvent.itemId;
                group.eventId = group.nowEvent.eventId; // Fix: Assign eventId for navigation
                group.totalQuantity = group.nowEvent.requestedQuantity || 0;
            }

            return group;
        });

        console.log('Grouped items (Timeline View):', this.groupedItems);
        this.fetchAvailabilityForGroups();
    }

    fetchAvailabilityForGroups() {
        const itemIds = Array.from(new Set(this.groupedItems.map(i => i.itemId).filter(id => id)));
        if (itemIds.length === 0) return;

        // Fetch availability for current date
        const todayStr = new Date().toISOString().split('T')[0];

        this.itemsService.getBulkAvailability(itemIds, 0, todayStr, todayStr).subscribe({
            next: (res) => {
                const map = new Map();
                res.forEach(avail => {
                    map.set(avail.itemId, avail);
                });
                this.availabilityMap.set(map);
            },
            error: (err) => console.error('Failed to fetch availability:', err)
        });
    }

    getAvailabilityForItem(itemId: number): any {
        return this.availabilityMap().get(itemId);
    }

    filterItems() {
        const q = this.searchQuery.toLowerCase();
        this.filteredItems = this.groupedItems.filter(i =>
            i.itemName?.toLowerCase().includes(q) ||
            i.brand?.toLowerCase().includes(q) ||
            i.model?.toLowerCase().includes(q) ||
            i.nowEvent?.eventName?.toLowerCase().includes(q) ||
            i.nextEvent?.eventName?.toLowerCase().includes(q)
        );
    }

    getItemsByCategory(category: string): any[] {
        return this.filteredItems.filter(item => item.category === category);
    }

    getItemDisplayName(item: any): string {
        if (!item) return '';
        const brand = item.brand || '';
        const model = item.model || '';
        return `${brand} ${model}`.trim() || item.itemName || 'Unknown Item';
    }

    openMoveModal(item: any) {
        console.log('Opening modal for item:', item);
        console.log('BEFORE reset - currentStep:', this.currentStep, 'type:', typeof this.currentStep);

        // Force reset currentStep FIRST to ensure clean state
        this.currentStep = 1;

        this.selectedItem = item;
        this.showModal = true;
        this.resetModalForm();

        console.log('AFTER reset - Modal state - showModal:', this.showModal, 'currentStep:', this.currentStep, 'selectedItem:', this.selectedItem);
        // Pre-load candidates from STORAGE (default)
        this.loadCandidateItems('STORAGE');
    }

    closeModal() {
        this.showModal = false;
        this.selectedItem = null;
    }


    onActionTypeChange() {
        // If Move to Store, we enforce replacing from Storage or just finding a rep?
        // Logic: Move to Store = Item A -> Storage. Event needs replacement B.
        // So effectively we need to select B (Replacement).
        // Implicitly B comes from Storage usually. 
        if (this.actionType === 'MOVE_TO_STORE') {
            this.targetType = 'STORAGE'; // Can only replace from storage if we just dumping to store?
            // Actually user can replace from anywhere, but let's keep it simple.
        }
        this.updateCandidates();
    }

    onTargetTypeChange() {
        this.targetEventId = null;
        this.targetItemId = null;
        this.updateCandidates();
    }

    onTargetEventChange() {
        if (this.targetType === 'EVENT' && this.targetEventId) {
            this.loadCandidateItems('EVENT', this.targetEventId);
        }
    }

    updateCandidates() {
        if (this.targetType === 'STORAGE') {
            this.loadCandidateItems('STORAGE');
        } else if (this.targetType === 'EVENT' && this.targetEventId) {
            this.loadCandidateItems('EVENT', this.targetEventId);
        }
    }

    loadCandidateItems(source: 'STORAGE' | 'EVENT', eventId?: number) {
        if (!this.selectedItem) return;
        const category = this.selectedItem.category;

        this.candidateItems = [];
        this.filteredCandidateItems = [];
        this.equipmentSearchQuery = '';

        if (source === 'STORAGE') {
            // Fetch items from inventory/storage using availability endpoint
            // Get items available for the next 30 days
            const startDate = new Date().toISOString().split('T')[0];
            const endDate = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

            this.http.get<any[]>(`/api/inventory/availability?startDate=${startDate}&endDate=${endDate}`).subscribe(res => {
                console.log('Inventory availability response:', res);

                // Group by brand + model
                const grouped = new Map<string, any>();

                res.filter(i =>
                    i.category === category &&
                    i.itemId !== this.selectedItem.itemId &&
                    i.available > 0 &&
                    (i.brand?.trim() || i.model?.trim() || (i.name?.trim() && i.name !== 'Unknown Item'))
                ).forEach(item => {
                    const brand = item.brand || '';
                    const model = item.model || '';
                    const key = `${brand}_${model}`.toLowerCase();

                    if (grouped.has(key)) {
                        const existing = grouped.get(key);
                        existing.availableQuantity += item.available;
                        existing.totalQuantity += item.totalQuantity;
                    } else {
                        grouped.set(key, {
                            id: item.itemId, // Use first ID for reference
                            brand: brand,
                            model: model,
                            category: item.category,
                            name: item.itemName,
                            availableQuantity: item.available,
                            totalQuantity: item.totalQuantity,
                            allocatedQuantity: item.allocated
                        });
                    }
                });

                this.candidateItems = Array.from(grouped.values());
                this.filteredCandidateItems = [...this.candidateItems];
                console.log('Grouped candidate items from storage:', this.candidateItems);
            });
        } else {
            // From Event
            this.http.get<any[]>(`/api/event-items/event/${eventId}`).subscribe(res => {
                this.candidateItems = res.filter(i =>
                    i.category === category &&
                    (i.brand?.trim() || i.model?.trim() || (i.itemName?.trim() && i.itemName !== 'Unknown Item'))
                ).map(ei => ({
                    id: ei.itemId,
                    brand: ei.brand,
                    model: ei.model,
                    category: ei.category,
                    name: ei.itemName,
                    eventName: ei.eventName, // Keep track of which event it's from
                    availableQuantity: ei.allocatedQuantity,
                    eventItemId: ei.id // This is the targetEventItemId
                }));
                this.filteredCandidateItems = [...this.candidateItems];
                console.log('Candidate items from event:', this.candidateItems);
            });
        }
    }

    filterCandidateItems() {
        const q = this.equipmentSearchQuery.toLowerCase();
        this.filteredCandidateItems = this.candidateItems.filter(item =>
            item.brand?.toLowerCase().includes(q) ||
            item.model?.toLowerCase().includes(q) ||
            item.name?.toLowerCase().includes(q)
        );
    }

    selectEquipment(item: any) {
        this.targetItemId = item.id;
        this.targetEventItemId = item.eventItemId || null;
        // Default quantity to the quantity of the item we are replacing
        this.swapQuantity = this.selectedItem?.nowEvent?.requestedQuantity || 1;
    }

    confirmAction() {
        if (!this.selectedItem) return;

        if (this.actionType === 'REPLACE_WITH' && !this.targetItemId) {
            this.toastService.show('Please select a replacement item', 'error');
            return;
        }

        if (this.swapQuantity <= 0) {
            this.toastService.show('Quantity must be at least 1', 'error');
            return;
        }

        const req = {
            sourceEventItemId: this.selectedItem.id,
            targetItemId: this.targetItemId,
            targetEventId: this.targetType === 'EVENT' ? this.targetEventId : undefined,
            targetEventItemId: this.targetEventItemId || undefined,
            targetType: this.targetType,
            moveToStore: this.actionType === 'MOVE_TO_STORE',
            swapMode: this.swapMode,
            quantity: this.swapQuantity,
            reason: this.reason,
            userId: this.currentUser?.id || 1 // fallback
        };

        if (!confirm(`Confirm ${this.actionType === 'MOVE_TO_STORE' ? 'Move to Store' : 'Swap'}? This cannot be undone.`)) return;

        this.eventService.swapItems(req as any).subscribe({
            next: () => {
                const eventId = this.selectedItem?.eventId;
                console.log('Swap success, navigating to event:', eventId);

                this.closeModal();
                if (eventId) {
                    this.router.navigate(['/history/event', eventId], { queryParams: { returnUrl: '/replace-items' } });
                } else {
                    this.loadData();
                }
            },
            error: (err) => {
                this.toastService.show('Error: ' + (err.error?.message || err.message), 'error');
            }
        });
    }

    goBack() {
        this.router.navigate(['/calendar']);
    }
}
