import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EventItemsService } from '../../../services/event-items.service';
import { EventService } from '../../../services/event.service';
import { ToastService } from '../../../services/toast.service';
import { ItemsService, Item } from '../../../services/Item.service';
import { UserService } from '../../../services/user.service';

interface DemandInterval {
    start: Date;
    end: Date;
    demand: number;
    shortage: number;
    isOverbooked: boolean;
}

interface ItemDashboardDetail {
    id: number;
    name: string;
    subName?: string;
    category: string;
    totalStock: number;
    maxDemand: number;
    maxShortage: number;
    intervals: DemandInterval[];
    allBookings: {
        eventId: number;
        eventTitle: string;
        startDate: string;
        endDate: string;
        qty: number;
    }[];
}

@Component({
    selector: 'app-dashboard-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './dashboard-page.component.html',
    styleUrls: ['./dashboard-page.component.scss']
})
export class DashboardPageComponent implements OnInit {
    private eventItemsService = inject(EventItemsService);
    private eventService = inject(EventService);
    private toastService = inject(ToastService);
    private itemsService = inject(ItemsService);
    private userService = inject(UserService);

    /** Only ADMIN and TECH_LEAD may decide repair vs. write-off */
    get canManageRepairs(): boolean {
        const role = this.userService.currentUser?.role;
        return role === 'ADMIN' || role === 'TECH_LEAD';
    }

    /** Real product title = brand + model (e.g. "YAMAHA DSP-RX"); the name field is just a description */
    getItemDisplayName(primary: any, fallback?: any): string {
        const brand = primary?.brand || fallback?.brand || fallback?.item?.brand || '';
        const model = primary?.model || fallback?.model || fallback?.item?.model || '';
        const brandModel = `${brand} ${model}`.trim();
        if (brandModel) return brandModel;
        return primary?.name || primary?.itemName || fallback?.itemName || fallback?.name || 'Item';
    }

    allEventItems = signal<any[]>([]);
    allInventoryItems = signal<Item[]>([]);
    isLoading = signal(false);
    activeTab = signal<'OVERVIEW' | 'OVERBOOKED' | 'TIMELINE' | 'INVENTORY' | 'MAINTENANCE'>('OVERVIEW');

    // Filtering
    selectedMonth = signal(new Date().getMonth() + 1); // 1-12
    selectedYear = signal(new Date().getFullYear());
    searchTerm = signal('');

    availableYears = [2024, 2025, 2026];
    months = [
        { value: 1, name: 'January' }, { value: 2, name: 'February' }, { value: 3, name: 'March' },
        { value: 4, name: 'April' }, { value: 5, name: 'May' }, { value: 6, name: 'June' },
        { value: 7, name: 'July' }, { value: 8, name: 'August' }, { value: 9, name: 'September' },
        { value: 10, name: 'October' }, { value: 11, name: 'November' }, { value: 12, name: 'December' }
    ];

    // Computed data for the dashboard
    monthlyData = computed(() => {
        const items = this.allEventItems();
        const month = this.selectedMonth();
        const year = this.selectedYear();

        return items.filter(ei => {
            if (!ei.eventStartDate) return false;
            const date = new Date(ei.eventStartDate);
            return (date.getMonth() + 1) === month && date.getFullYear() === year;
        });
    });

    // Detailed Item Analysis (shared by multiple tabs)
    itemDetails = computed(() => {
        const data = this.monthlyData();
        const inventory = this.allInventoryItems();
        const search = this.searchTerm().toLowerCase();

        const detailsMap = new Map<number, ItemDashboardDetail>();

        // Pre-group bookings by item
        data.forEach(ei => {
            const itemId = ei.itemId;
            if (!itemId) return;

            const invItem = inventory.find(i => i.id === itemId);
            const stock = invItem ? (invItem.totalQuantity || 0) : (ei.totalQuantity || 0);

            if (!detailsMap.has(itemId)) {
                detailsMap.set(itemId, {
                    id: itemId,
                    name: this.getItemDisplayName(invItem, ei),
                    subName: invItem ? (invItem.name || invItem.itemName || ei.itemName) : ei.itemName,
                    category: invItem ? (invItem.category || ei.category) : (ei.category || 'OTHER'),
                    totalStock: stock,
                    maxDemand: 0,
                    maxShortage: 0,
                    intervals: [],
                    allBookings: []
                });
            }

            const detail = detailsMap.get(itemId)!;
            detail.allBookings.push({
                eventId: ei.eventId,
                eventTitle: ei.eventName,
                startDate: ei.eventStartDate,
                endDate: ei.eventEndDate,
                qty: Number(ei.requestedQuantity) || 0
            });
        });

        // Calculate Availability Intervals for each item
        const results = Array.from(detailsMap.values()).map(detail => {
            const intervals = this.calculateDemandIntervals(detail.allBookings, detail.totalStock);
            const maxDemand = Math.max(0, ...intervals.map(i => i.demand));
            const maxShortage = Math.max(0, ...intervals.map(i => i.shortage));

            return {
                ...detail,
                intervals,
                maxDemand,
                maxShortage
            };
        });

        // Filter by search (matches model name, description, or category)
        if (search) {
            return results.filter(r =>
                r.name.toLowerCase().includes(search) ||
                (r.subName || '').toLowerCase().includes(search) ||
                r.category.toLowerCase().includes(search)
            );
        }

        return results;
    });

    private calculateDemandIntervals(bookings: any[], stock: number): DemandInterval[] {
        if (bookings.length === 0) return [];

        // 1. Get all unique date boundaries
        const datePoints = new Set<number>();
        bookings.forEach(b => {
            const start = new Date(b.startDate);
            const end = new Date(b.endDate);
            // End is inclusive, so interval point is the day AFTER end
            const nextDay = new Date(end);
            nextDay.setDate(nextDay.getDate() + 1);

            datePoints.add(start.setHours(0, 0, 0, 0));
            datePoints.add(nextDay.setHours(0, 0, 0, 0));
        });

        const sortedPoints = Array.from(datePoints).sort((a, b) => a - b);
        const intervals: DemandInterval[] = [];

        // 2. Create intervals and sum demand
        for (let i = 0; i < sortedPoints.length - 1; i++) {
            const iStart = new Date(sortedPoints[i]);
            const iEnd = new Date(sortedPoints[i + 1]);
            // Move iEnd one day back to represent inclusive end of the interval
            const displayEnd = new Date(iEnd);
            displayEnd.setDate(displayEnd.getDate() - 1);

            let demand = 0;
            bookings.forEach(b => {
                const bStart = new Date(b.startDate).setHours(0, 0, 0, 0);
                const bEnd = new Date(b.endDate).setHours(0, 0, 0, 0);

                // If event overlaps with [iStart, iEnd)
                if (bStart < iEnd.getTime() && bEnd >= iStart.getTime()) {
                    demand += b.qty;
                }
            });

            if (demand > 0) {
                intervals.push({
                    start: iStart,
                    end: displayEnd,
                    demand,
                    shortage: Math.max(0, demand - stock),
                    isOverbooked: demand > stock
                });
            }
        }

        // Merge adjacent identical demand intervals
        const merged: DemandInterval[] = [];
        intervals.forEach(curr => {
            const last = merged[merged.length - 1];
            if (last && last.demand === curr.demand) {
                last.end = curr.end;
            } else {
                merged.push({ ...curr });
            }
        });

        return merged;
    }

    overbookedItems = computed(() => {
        return this.itemDetails().filter(d => d.maxShortage > 0);
    });

    itemTimeline = computed(() => {
        // Timeline Tab: Items that are NOT overbooked (usage only)
        return this.itemDetails().filter(d => d.maxShortage === 0 && d.maxDemand > 0);
    });

    // Keep stats simple
    stats = computed(() => {
        const data = this.monthlyData();
        const uniqueEvents = new Set(data.map(ei => ei.eventId).filter(id => id != null));
        const totalQty = data.reduce((sum, ei) => sum + (Number(ei.requestedQuantity) || 0), 0);

        return {
            eventCount: uniqueEvents.size,
            itemUsageCount: data.length,
            totalQuantity: totalQty
        };
    });

    inventoryHealth = computed(() => {
        return this.itemDetails().map(d => ({
            id: d.id,
            name: d.name,
            subName: d.subName,
            category: d.category,
            totalStock: d.totalStock,
            currentlyBooked: d.maxDemand, // Use peak demand for health
            remaining: Math.max(0, d.totalStock - d.maxDemand),
            utilization: d.totalStock > 0 ? (d.maxDemand / d.totalStock) * 100 : 0
        })).sort((a, b) => b.utilization - a.utilization);
    });

    categoryDistribution = computed(() => {
        const data = this.monthlyData();
        const distMap = new Map<string, number>();

        data.forEach(ei => {
            const cat = ei.category || 'OTHER';
            distMap.set(cat, (distMap.get(cat) || 0) + (Number(ei.requestedQuantity) || 0));
        });

        const total = Array.from(distMap.values()).reduce((a, b) => a + b, 0);

        return Array.from(distMap.entries()).map(([name, value]) => ({
            name,
            value,
            percentage: total > 0 ? (value / total) * 100 : 0
        })).sort((a, b) => b.value - a.value);
    });

    topItems = computed(() => {
        return this.itemDetails()
            .sort((a, b) => {
                const totalA = a.allBookings.reduce((sum, b) => sum + b.qty, 0);
                const totalB = b.allBookings.reduce((sum, b) => sum + b.qty, 0);
                return totalB - totalA;
            })
            .slice(0, 10)
            .map(d => ({
                name: d.name,
                subName: d.subName,
                count: d.allBookings.length,
                totalQty: d.allBookings.reduce((sum, b) => sum + b.qty, 0)
            }));
    });

    repairItems = computed(() => {
        return this.allInventoryItems().filter(item => {
            const repairQty = item.spec?.repair_qty || 0;
            return repairQty > 0 || item.status === 'UNDER_REPAIR' || item.status === 'DAMAGED';
        });
    });

    /** Total broken units across all items, for the maintenance alert */
    totalRepairUnits = computed(() =>
        this.repairItems().reduce((sum, item) => sum + (item.spec?.repair_qty || 1), 0));

    ngOnInit(): void {
        this.loadData();
    }

    loadData(): void {
        this.isLoading.set(true);
        // Load event items for usage stats
        this.eventItemsService.getAllEventItems().subscribe({
            next: (data) => {
                this.allEventItems.set(data);
                this.isLoading.set(false);
            },
            error: (err) => {
                this.toastService.show('Failed to load usage data', 'error');
                this.isLoading.set(false);
            }
        });

        // Load full inventory for maintenance/repair info
        this.itemsService.getItems().subscribe({
            next: (items) => {
                this.allInventoryItems.set(items);
            }
        });
    }

    // === RESOLVE REPAIR (Tech Lead / Admin only) ===
    resolveItem = signal<Item | null>(null);
    resolveQty = signal(1);

    openResolveModal(item: Item): void {
        if (!this.canManageRepairs) {
            this.toastService.show('Only Tech Lead or Admin can resolve repairs', 'error');
            return;
        }
        this.resolveItem.set(item);
        this.resolveQty.set(item.spec?.repair_qty || 1);
    }

    closeResolveModal(): void {
        this.resolveItem.set(null);
    }

    maxResolveQty(): number {
        return this.resolveItem()?.spec?.repair_qty || 1;
    }

    private validResolveQty(): number | null {
        const qty = Number(this.resolveQty());
        if (!Number.isInteger(qty) || qty <= 0 || qty > this.maxResolveQty()) {
            this.toastService.show(`Quantity must be between 1 and ${this.maxResolveQty()}`, 'error');
            return null;
        }
        return qty;
    }

    /** Repaired: units go back into usable stock */
    confirmRepaired(): void {
        const item = this.resolveItem();
        const qty = this.validResolveQty();
        if (!item || qty === null) return;

        this.itemsService.releaseItemFromRepair(item.id, qty).subscribe({
            next: () => {
                this.toastService.show(`Fixed ${qty} units of ${this.getItemDisplayName(item)} — returned to stock`, 'success');
                this.closeResolveModal();
                this.loadData();
            },
            error: (err) => this.toastService.show('Failed: ' + (err?.error?.message || err.message), 'error')
        });
    }

    /** Beyond repair: units are permanently removed from total stock */
    confirmWriteOff(): void {
        const item = this.resolveItem();
        const qty = this.validResolveQty();
        if (!item || qty === null) return;

        this.itemsService.writeOffItem(item.id, qty).subscribe({
            next: () => {
                this.toastService.show(`Wrote off ${qty} units of ${this.getItemDisplayName(item)} — removed from stock`, 'warning');
                this.closeResolveModal();
                this.loadData();
            },
            error: (err) => this.toastService.show('Failed: ' + (err?.error?.message || err.message), 'error')
        });
    }

    onMarkBroken(item: Item): void {
        this.itemsService.markItemForRepair(item.id, 1).subscribe({
            next: () => {
                this.toastService.show(`Marked ${item.name} for repair`, 'warning');
                this.loadData();
            }
        });
    }

    onPeriodChange(): void {
        // Computed signals will handle the update
    }

    getCatColor(index: number): string {
        const colors = [
            '#38bdf8', '#818cf8', '#34d399', '#fbbf24',
            '#f87171', '#a78bfa', '#2dd4bf', '#fb7185'
        ];
        return colors[index % colors.length];
    }

    getPieGradient(): string {
        const dist = this.categoryDistribution();
        if (dist.length === 0) return 'rgba(255,255,255,0.05)';

        let currentTotal = 0;
        const stops = dist.map((d, i) => {
            const start = currentTotal;
            currentTotal += d.percentage;
            return `${this.getCatColor(i)} ${start}% ${currentTotal}%`;
        });

        return `conic-gradient(${stops.join(', ')})`;
    }
}
