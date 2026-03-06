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
    this.loadEvents();
  }

  loadEvents() {
    this.isLoading = true;
    this.eventService.getEvents().subscribe({
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

        // Extract available years for filter
        const yearSet = new Set<number>();
        this.pastEvents.forEach(e => yearSet.add(new Date(e.startDate).getFullYear()));
        this.availableYears = Array.from(yearSet).sort((a, b) => b - a);

        this.applyFilters();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load events:', err);
        this.isLoading = false;
      }
    });
  }

  setTab(tab: 'upcoming' | 'ongoing' | 'past') {
    this.activeTab = tab;
    this.applyFilters();
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

      // Past tab specific period filtering
      if (this.activeTab === 'past') {
        const d = new Date(e.startDate);
        const matchesYear = !this.selectedYear || d.getFullYear() === this.selectedYear;
        const matchesMonth = !this.selectedMonth || this.getMonthName(d.getMonth()) === this.selectedMonth;
        return matchesSearch && matchesYear && matchesMonth;
      }

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
