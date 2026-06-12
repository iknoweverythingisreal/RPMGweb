import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EventItemsService } from '../../../services/event-items.service';
import { ToastService } from '../../../services/toast.service';
import { ItemsService } from '../../../services/Item.service';

@Component({
    selector: 'app-summary-all-items-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './summary-all-items-page.component.html',
    styleUrls: ['./summary-all-items-page.component.scss']
})
export class SummaryAllItemsPageComponent implements OnInit {
    private eventItemsService = inject(EventItemsService);
    private toastService = inject(ToastService);
    private itemsService = inject(ItemsService);

    allData = signal<any[]>([]);
    isLoading = signal(false);
    searchTerm = signal('');

    itemsSummary = computed(() => {
        const data = this.allData();
        const summaryMap = new Map<number, {
            id: number,
            name: string,
            category: string,
            totalUsage: number,
            eventCount: number,
            lastUsed: string
        }>();

        data.forEach(ei => {
            const item = ei.item;
            if (!item) return;

            const current = summaryMap.get(item.id) || {
                id: item.id,
                name: item.name,
                category: item.category || 'N/A',
                totalUsage: 0,
                eventCount: 0,
                lastUsed: ''
            };

            current.totalUsage += (ei.requestedQuantity || 0);
            current.eventCount += 1;

            const eventDate = ei.event?.startDate;
            if (eventDate && (!current.lastUsed || eventDate > current.lastUsed)) {
                current.lastUsed = eventDate;
            }

            summaryMap.set(item.id, current);
        });

        return Array.from(summaryMap.values());
    });

    filteredSummary = computed(() => {
        const term = this.searchTerm().toLowerCase();
        return this.itemsSummary().filter(item =>
            item.name.toLowerCase().includes(term) ||
            item.category.toLowerCase().includes(term)
        ).sort((a, b) => b.totalUsage - a.totalUsage);
    });

    ngOnInit(): void {
        this.loadData();
    }

    loadData(): void {
        this.isLoading.set(true);
        this.eventItemsService.getAllEventItems().subscribe({
            next: (data) => {
                this.allData.set(data);
                this.isLoading.set(false);
            },
            error: (err) => {
            }
        });
    }

    onReportDamage(item: any): void {
        const qtyStr = window.prompt(`How many units of ${item.name} are broken?`, '1');
        if (!qtyStr) return;

        const qty = parseInt(qtyStr, 10);
        if (isNaN(qty) || qty <= 0) {
            this.toastService.show('Please enter a valid quantity', 'error');
            return;
        }

        this.itemsService.markItemForRepair(item.id, qty).subscribe({
            next: () => {
                this.toastService.show(`Reported ${qty} units of ${item.name} as DAMAGED`, 'warning');
                this.loadData();
            },
            error: (err) => this.toastService.show('Failed to report damage: ' + (err?.error?.message || err.message), 'error')
        });
    }
}
