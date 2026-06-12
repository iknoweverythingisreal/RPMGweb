import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EventService, EventHistoryEntry } from '../../../services/event.service';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../services/toast.service';

@Component({
    selector: 'app-system-log-page',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './system-log-page.component.html',
    styleUrls: ['./system-log-page.component.scss']
})
export class SystemLogPageComponent implements OnInit {
    private eventService = inject(EventService);
    public userService = inject(UserService);
    private toastService = inject(ToastService);
    public router = inject(Router);

    logs: EventHistoryEntry[] = [];
    filteredLogs: EventHistoryEntry[] = [];
    isLoading = false;
    searchTerm = '';
    filterType = 'ALL';

    private userNames = new Map<number, string>();
    private eventNames = new Map<number, string>();

    ngOnInit(): void {
        this.loadLogs();
        this.loadLookups();
    }

    /** Resolve user IDs and event IDs into real names */
    private loadLookups(): void {
        this.eventService.getUsersLight().subscribe({
            next: (users) => users.forEach(u => this.userNames.set(Number(u.id), u.displayName)),
            error: () => { /* fall back to User #id */ }
        });
        this.eventService.getEvents().subscribe({
            next: (events) => events.forEach((ev: any) => {
                if (ev?.id != null) this.eventNames.set(Number(ev.id), ev.title || ev.name || '');
            }),
            error: () => { /* fall back to Event #id */ }
        });
    }

    getUserName(userId?: number): string {
        if (userId == null) return 'System';
        return this.userNames.get(Number(userId)) || `User #${userId}`;
    }

    getEventName(eventId?: number): string {
        if (eventId == null) return '-';
        return this.eventNames.get(Number(eventId)) || `Event #${eventId}`;
    }

    /** Backend serializes the action timestamp as changedAt (older endpoints used createdAt) */
    getLogTime(log: EventHistoryEntry): string | null {
        return log.createdAt || log.changedAt || null;
    }

    goBack(): void {
        this.router.navigate(['/calendar']);
    }

    loadLogs(): void {
        this.isLoading = true;
        this.eventService.getAllHistory().subscribe({
            next: (data) => {
                this.logs = data;
                this.applyFilter();
                this.isLoading = false;
            },
            error: (err) => {
                this.toastService.show('Failed to load system logs', 'error');
                this.isLoading = false;
            }
        });
    }

    applyFilter(): void {
        const term = this.searchTerm.toLowerCase();
        this.filteredLogs = this.logs.filter(log => {
            const matchesSearch = !this.searchTerm ||
                (log.note?.toLowerCase().includes(term)) ||
                (log.action?.toLowerCase().includes(term)) ||
                (log.eventId?.toString().includes(this.searchTerm)) ||
                this.getEventName(log.eventId).toLowerCase().includes(term) ||
                this.getUserName(log.userId).toLowerCase().includes(term);

            const matchesType = this.filterType === 'ALL' || log.action === this.filterType;

            return matchesSearch && matchesType;
        });
    }

    getLogIcon(action: string | undefined): string {
        if (!action) return 'fa-info-circle';
        const a = action.toUpperCase();
        if (a.includes('CREATE')) return 'fa-plus-circle';
        if (a.includes('UPDATE') || a.includes('EDIT')) return 'fa-edit';
        if (a.includes('DELETE')) return 'fa-trash-alt';
        if (a.includes('CONFIRM')) return 'fa-check-double';
        if (a.includes('PREPARE')) return 'fa-box';
        if (a.includes('CHECK')) return 'fa-clipboard-check';
        return 'fa-history';
    }

    getLogColor(action: string | undefined): string {
        if (!action) return 'var(--text-secondary)';
        const a = action.toUpperCase();
        if (a.includes('CREATE')) return '#10b981'; // green
        if (a.includes('UPDATE') || a.includes('EDIT')) return '#3b82f6'; // blue
        if (a.includes('DELETE')) return '#ef4444'; // red
        if (a.includes('CONFIRM')) return '#8b5cf6'; // purple
        return 'var(--text-main)';
    }

    formatData(data: any): string {
        if (!data) return '';
        try {
            return JSON.stringify(data, null, 2);
        } catch {
            return '';
        }
    }

    hasData(log: EventHistoryEntry): boolean {
        return !!(log.data && Object.keys(log.data).length > 0);
    }
}
