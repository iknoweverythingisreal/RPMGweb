import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EventService } from '../../../services/event.service';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../services/toast.service';
import { Router } from '@angular/router';

@Component({
    selector: 'app-system-log-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './system-log-page.component.html',
    styleUrls: ['./system-log-page.component.scss']
})
export class SystemLogPageComponent implements OnInit {
    logs = signal<any[]>([]);
    filteredLogs = signal<any[]>([]);
    isLoading = signal<boolean>(false);

    // Filters
    searchTerm = '';
    selectedAction = 'ALL';
    startDate = '';
    endDate = '';

    actions = ['ALL', 'CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE', 'ROOM_ASSIGN', 'PERSONNEL_ASSIGN'];

    constructor(
        private eventService: EventService,
        private userService: UserService,
        private toastService: ToastService,
        private router: Router
    ) { }

    ngOnInit() {
        this.loadLogs();
    }

    loadLogs() {
        this.isLoading.set(true);
        // Note: We need a global history endpoint. For now, we'll fetch recently active event histories or a new global one if implemented.
        // Assuming we implement getAllHistory in EventService
        this.eventService.getEventHistory(0).subscribe({ // 0 or -1 could represent "all" if backend supports it
            next: (data) => {
                this.logs.set(data);
                this.applyFilters();
                this.isLoading.set(false);
            },
            error: (err) => {
                this.toastService.show('Failed to load system logs', 'error');
                this.isLoading.set(false);
            }
        });
    }

    applyFilters() {
        let filtered = this.logs();

        if (this.searchTerm) {
            const term = this.searchTerm.toLowerCase();
            filtered = filtered.filter(l =>
                l.note?.toLowerCase().includes(term) ||
                l.user?.name?.toLowerCase().includes(term) ||
                l.eventId?.toString().includes(term)
            );
        }

        if (this.selectedAction !== 'ALL') {
            filtered = filtered.filter(l => l.action === this.selectedAction);
        }

        if (this.startDate) {
            filtered = filtered.filter(l => new Date(l.createdAt) >= new Date(this.startDate));
        }

        if (this.endDate) {
            const e = new Date(this.endDate);
            e.setHours(23, 59, 59);
            filtered = filtered.filter(l => new Date(l.createdAt) <= e);
        }

        this.filteredLogs.set(filtered);
    }

    formatDate(date: string) {
        return new Date(date).toLocaleString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    viewEvent(eventId: number) {
        this.router.navigate(['/history/event', eventId]);
    }

    getDiff(log: any) {
        if (!log.data) return null;
        try {
            return JSON.stringify(log.data, null, 2);
        } catch {
            return null;
        }
    }
}
