import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EventItemsService } from '../../../services/event-items.service';
import { ToastService } from '../../../services/toast.service';
import { DateTimeHelper } from '../../../services/event.service';

@Component({
    selector: 'app-item-summary-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './item-summary-page.component.html',
    styleUrls: ['./item-summary-page.component.scss']
})
export class ItemSummaryPageComponent implements OnInit {
    summaryData = signal<any[]>([]);
    isLoading = signal<boolean>(false);
    now = new Date();

    // Reporting Period
    selectedMonth: number;
    selectedYear: number;
    months = [
        { value: 0, name: 'January' }, { value: 1, name: 'February' }, { value: 2, name: 'March' },
        { value: 3, name: 'April' }, { value: 4, name: 'May' }, { value: 5, name: 'June' },
        { value: 6, name: 'July' }, { value: 7, name: 'August' }, { value: 8, name: 'September' },
        { value: 9, name: 'October' }, { value: 10, name: 'November' }, { value: 11, name: 'December' }
    ];
    years: number[] = [];

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
        this.loadSummary();
    }

    loadSummary() {
        this.isLoading.set(true);
        const start = new Date(this.selectedYear, this.selectedMonth, 1);
        const end = new Date(this.selectedYear, this.selectedMonth + 1, 0);

        const startDate = DateTimeHelper.toLocalDate(start);
        const endDate = DateTimeHelper.toLocalDate(end);

        // Assuming we implement a summary endpoint in back-end or use existing one and aggregate
        this.eventItemsService.getAvailability(startDate, endDate).subscribe({
            next: (data) => {
                this.summaryData.set(data);
                this.isLoading.set(false);
            },
            error: (err) => {
                this.toastService.show('Failed to load item summary', 'error');
                this.isLoading.set(false);
            }
        });
    }

    printReport() {
        window.print();
    }
}
