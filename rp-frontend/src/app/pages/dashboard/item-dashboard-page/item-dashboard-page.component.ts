import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EventItemsService } from '../../../services/event-items.service';
import { ToastService } from '../../../services/toast.service';
import { DateTimeHelper } from '../../../services/event.service';

@Component({
    selector: 'app-item-dashboard-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './item-dashboard-page.component.html',
    styleUrls: ['./item-dashboard-page.component.scss']
})
export class ItemDashboardPageComponent implements OnInit {
    items = signal<any[]>([]);
    filteredItems = signal<any[]>([]);
    isLoading = signal<boolean>(false);

    // Period Selection
    selectedMonth: number;
    selectedYear: number;
    months = [
        { value: 0, name: 'January' }, { value: 1, name: 'February' }, { value: 2, name: 'March' },
        { value: 3, name: 'April' }, { value: 4, name: 'May' }, { value: 5, name: 'June' },
        { value: 6, name: 'July' }, { value: 7, name: 'August' }, { value: 8, name: 'September' },
        { value: 9, name: 'October' }, { value: 10, name: 'November' }, { value: 11, name: 'December' }
    ];
    years: number[] = [];

    searchTerm = '';
    showShortagesOnly = false;

    get shortageCount(): number {
        return this.items().filter(it => it.shortageCount > 0).length;
    }

    constructor(
        private eventItemsService: EventItemsService,
        private toastService: ToastService
    ) {
        const now = new Date();
        this.selectedMonth = now.getMonth();
        this.selectedYear = now.getFullYear();

        for (let i = now.getFullYear() - 2; i <= now.getFullYear() + 2; i++) {
            this.years.push(i);
        }
    }

    ngOnInit() {
        this.loadData();
    }

    loadData() {
        this.isLoading.set(true);
        const start = new Date(this.selectedYear, this.selectedMonth, 1);
        const end = new Date(this.selectedYear, this.selectedMonth + 1, 0);

        const startDate = DateTimeHelper.toLocalDate(start);
        const endDate = DateTimeHelper.toLocalDate(end);

        this.eventItemsService.getAvailability(startDate, endDate).subscribe({
            next: (data) => {
                // Assume data returns inventory items with usage counts and shortages
                this.items.set(data || []);
                this.applyFilters();
                this.isLoading.set(false);
            },
            error: (err) => {
                this.toastService.show('Failed to load dashboard data', 'error');
                this.isLoading.set(false);
            }
        });
    }

    applyFilters() {
        let filtered = this.items();

        if (this.searchTerm) {
            const term = this.searchTerm.toLowerCase();
            filtered = filtered.filter(it =>
                it.name?.toLowerCase().includes(term) ||
                it.category?.name?.toLowerCase().includes(term)
            );
        }

        if (this.showShortagesOnly) {
            filtered = filtered.filter(it => it.shortageCount > 0);
        }

        this.filteredItems.set(filtered);
    }

    getUsagePercentage(item: any): number {
        if (!item.totalStock || item.totalStock === 0) return 0;
        return Math.round((item.maxBooked / item.totalStock) * 100);
    }
}
