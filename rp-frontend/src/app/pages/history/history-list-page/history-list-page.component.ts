import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { EventService } from '../../../services/event.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-history-list-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './history-list-page.component.html',
  styleUrls: ['./history-list-page.component.scss']
})
export class HistoryListPageComponent implements OnInit {
  // Original raw data
  ongoingEvents: any[] = [];
  upcomingEvents: any[] = [];
  pastEvents: any[] = [];

  // Data for UI display
  activeTab: 'upcoming' | 'ongoing' | 'past' = 'upcoming';
  filteredEvents: any[] = [];

  // Filter states
  searchQuery: string = '';
  selectedYear: number | null = null;
  selectedMonth: string | null = null;

  // Lookups for filters
  availableYears: number[] = [];
  months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];

  isLoading = false;

  constructor(
    private eventService: EventService,
    private router: Router
  ) { }

  ngOnInit() {
    this.initializeFilters();
    this.loadEvents();
  }

  private initializeFilters() {
    const currentYear = new Date().getFullYear();
    this.availableYears = [currentYear - 2, currentYear - 1, currentYear, currentYear + 1, currentYear + 2];
    this.selectedYear = currentYear;
  }

  loadEvents() {
    this.isLoading = true;

    // Determine range based on active tab and filters
    const now = new Date();
    let fromDate: Date, toDate: Date;

    if (this.activeTab === 'upcoming') {
      fromDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      toDate = new Date(now.getFullYear() + 1, now.getMonth(), now.getDate());
    } else if (this.activeTab === 'ongoing') {
      fromDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 7);
      toDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 7);
    } else {
      // Past tab - use filters
      const year = this.selectedYear || now.getFullYear();
      if (this.selectedMonth) {
        const monthIdx = this.months.indexOf(this.selectedMonth);
        fromDate = new Date(year, monthIdx, 1);
        toDate = new Date(year, monthIdx + 1, 0);
      } else {
        fromDate = new Date(year, 0, 1);
        toDate = new Date(year, 11, 31);
      }
    }

    const from = this.toLocalDate(fromDate);
    const to = this.toLocalDate(toDate);

    this.eventService.getEventsRange(from, to).subscribe({
      next: (events) => {
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        this.ongoingEvents = [];
        this.upcomingEvents = [];
        this.pastEvents = [];

        events.forEach(e => {
          const start = new Date(e.startDate);
          start.setHours(0, 0, 0, 0);
          const end = e.endDate ? new Date(e.endDate) : start;
          end.setHours(23, 59, 59, 999);

          if (today >= start && today <= end) {
            this.ongoingEvents.push(e);
          } else if (start > today) {
            this.upcomingEvents.push(e);
          } else {
            this.pastEvents.push(e);
          }
        });

        // Basic sorting
        this.ongoingEvents.sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime());
        this.upcomingEvents.sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime());
        this.pastEvents.sort((a, b) => new Date(b.startDate).getTime() - new Date(a.startDate).getTime());

        this.applyFilters();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load events:', err);
        this.isLoading = false;
      }
    });
  }

  private toLocalDate(date: Date): string {
    const y = date.getFullYear();
    const m = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  setTab(tab: 'upcoming' | 'ongoing' | 'past') {
    this.activeTab = tab;
    this.loadEvents(); // Re-fetch for new tab/range
  }

  onFilterChange() {
    if (this.activeTab === 'past') {
      this.loadEvents(); // Re-fetch for new filter range
    } else {
      this.applyFilters();
    }
  }

  applyFilters() {
    let source: any[] = [];
    if (this.activeTab === 'upcoming') source = this.upcomingEvents;
    else if (this.activeTab === 'ongoing') source = this.ongoingEvents;
    else source = this.pastEvents;

    const q = this.searchQuery.toLowerCase().trim();

    this.filteredEvents = source.filter(e => {
      // Search matching
      const matchesSearch = !q ||
        e.title?.toLowerCase().includes(q) ||
        e.location?.toLowerCase().includes(q);

      return matchesSearch;
    });
  }

  getMonthName(monthIdx: number): string {
    return this.months[monthIdx];
  }

  viewEventDetail(eventId: number) {
    this.router.navigate(['/history/event', eventId], { queryParams: { returnUrl: '/history' } });
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  goBack() {
    this.router.navigate(['/calendar']);
  }
}
