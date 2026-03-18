import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  EventService,
  DateTimeHelper,
  CalendarOwner
} from '../../services/event.service';
import { UserService } from '../../services/user.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-select-event-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './select-event-page.component.html',
  styleUrls: ['./select-event-page.component.scss']
})
export class SelectEventPageComponent implements OnInit {
  events: any[] = [];
  sortedEvents: any[] = [];
  filteredEvents: any[] = []; // For search results
  groupedEvents: { monthYear: string; events: any[] }[] = []; // NEW: Grouped by month
  searchQuery: string = '';
  selectedMonth: number = -1; // -1 = All
  selectedYear: number = -1;  // -1 = All
  months: { value: number, label: string }[] = [];
  years: number[] = [];
  isLoading = false;

  constructor(
    private router: Router,
    private eventService: EventService,
    public userService: UserService,
    private toastService: ToastService
  ) { }

  get isAdmin(): boolean {
    return this.userService.isAdmin;
  }

  get isManager(): boolean {
    return this.userService.isManager;
  }

  ngOnInit() {
    this.loadEvents();
  }

  /**
   * Load events within a relevant window (e.g., current month/view)
   */
  loadEvents() {
    this.isLoading = true;

    // Calculate window: -1 month to +6 months to cover typical selection needs
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const end = new Date(now.getFullYear(), now.getMonth() + 7, 0);

    const from = DateTimeHelper.toLocalDate(start);
    const to = DateTimeHelper.toLocalDate(end);

    this.eventService.getEventsRange(from, to).subscribe({
      next: (events) => {
        this.events = events;
        // Sort by startDate
        this.sortedEvents = [...events].sort((a, b) => {
          const today = new Date();
          today.setHours(0, 0, 0, 0);
          const endA = new Date(a.endDate);
          const endB = new Date(b.endDate);
          const pastA = endA < today;
          const pastB = endB < today;

          // Past events go to the bottom
          if (pastA && !pastB) return 1;
          if (!pastA && pastB) return -1;

          // Among non-past: sort by startDate ascending (soonest first)
          // Among past: sort by endDate descending (most recent past first)
          const dateA = new Date(a.startDate);
          const dateB = new Date(b.startDate);
          return pastA
            ? dateB.getTime() - dateA.getTime()
            : dateA.getTime() - dateB.getTime();
        });
        this.filteredEvents = this.sortedEvents;
        this.initializeFilters();
        this.onSearchChange();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load events:', err);
        this.toastService.show('ไม่สามารถโหลด Events ได้', 'error');
        this.isLoading = false;
      }
    });
  }

  /**
   * Filter events based on search query and dropdowns
   */
  onSearchChange() {
    const query = this.searchQuery.toLowerCase().trim();
    const selMonth = Number(this.selectedMonth);
    const selYear = Number(this.selectedYear);

    this.filteredEvents = this.sortedEvents.filter(event => {
      const matchesSearch = !query ||
        event.title?.toLowerCase().includes(query) ||
        event.location?.toLowerCase().includes(query) ||
        event.description?.toLowerCase().includes(query);

      const date = new Date(event.startDate);
      const matchesMonth = selMonth === -1 || date.getMonth() === selMonth;
      const matchesYear = selYear === -1 || date.getFullYear() === selYear;

      return matchesSearch && matchesMonth && matchesYear;
    });

    this.groupEventsByMonth(this.filteredEvents);
  }

  /**
   * Handle Year dropdown change
   */
  onYearChange() {
    if (Number(this.selectedYear) === -1) {
      this.selectedMonth = -1;
    }
    this.onSearchChange();
  }

  /**
   * Initialize month and year filter options
   */
  private initializeFilters() {
    // Months (Thai)
    const thaiMonths = [
      'มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน',
      'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม'
    ];
    this.months = thaiMonths.map((label, index) => ({ value: index, label }));

    // Years (Standard range to avoid empty dropdown)
    const currentYear = new Date().getFullYear();
    this.years = [currentYear - 2, currentYear - 1, currentYear, currentYear + 1, currentYear + 2];
  }

  /**
   * Group events by month and year
   */
  private groupEventsByMonth(events: any[]): void {
    const groups = new Map<string, any[]>();
    const now = new Date();
    const currentMonthYear = now.toLocaleDateString('th-TH', {
      year: 'numeric',
      month: 'long'
    });

    events.forEach(event => {
      const date = new Date(event.startDate);
      // Format as Thai month and year (e.g., "มกราคม 2026")
      const monthYear = date.toLocaleDateString('th-TH', {
        year: 'numeric',
        month: 'long'
      });

      if (!groups.has(monthYear)) {
        groups.set(monthYear, []);
      }
      groups.get(monthYear)!.push(event);
    });

    // Convert to array and sort events within each group: active/upcoming first, past last
    this.groupedEvents = Array.from(groups.entries())
      .map(([monthYear, eventsInGroup]) => {
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        const sorted = [...eventsInGroup].sort((a, b) => {
          const endA = new Date(a.endDate);
          const endB = new Date(b.endDate);
          const pastA = endA < today;
          const pastB = endB < today;

          if (pastA && !pastB) return 1;
          if (!pastA && pastB) return -1;

          const dateA = new Date(a.startDate);
          const dateB = new Date(b.startDate);
          return pastA
            ? dateB.getTime() - dateA.getTime()
            : dateA.getTime() - dateB.getTime();
        });

        return { monthYear, events: sorted };
      });

    // Sort: Current Month first, then others descending by date
    this.groupedEvents.sort((a, b) => {
      if (a.monthYear === currentMonthYear) return -1;
      if (b.monthYear === currentMonthYear) return 1;

      const dateA = new Date(a.events[0].startDate);
      const dateB = new Date(b.events[0].startDate);
      return dateA.getTime() - dateB.getTime();
    });
  }

  /**
   * Check if the monthYear string matches the current month and year
   */
  isCurrentMonth(monthYear: string): boolean {
    const now = new Date();
    const currentMonthYear = now.toLocaleDateString('th-TH', {
      year: 'numeric',
      month: 'long'
    });
    return monthYear === currentMonthYear;
  }

  /**
   * Navigate to Event Detail page (History/Info)
   */
  goToEventInfo(eventId: number) {
    this.router.navigate(['/history/event', eventId], { queryParams: { returnUrl: '/inventory/select-event' } });
  }

  /**
   * Navigate to Equipment Management text (Inventory)
   */
  goToEquipment(eventId: number) {
    this.router.navigate(['/inventory/event', eventId]);
  }

  /**
   * Deprecated: Use goToEquipment or goToEventInfo instead
   */
  selectEvent(eventId: number) {
    this.goToEquipment(eventId);
  }

  /**
   * Go back to Calendar page
   */
  goBack() {
    this.router.navigate(['/calendar']);
  }

  /**
   * Navigate to Calendar to create new event
   */
  createNewEvent() {
    this.router.navigate(['/calendar'], { queryParams: { openModal: 'true' } });
  }

  /**
   * Check if event is in the past
   */
  isPastEvent(event: any): boolean {
    if (!event.endDate) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const end = new Date(event.endDate);
    end.setHours(0, 0, 0, 0);
    return end < today;
  }

  /**
   * Strip HTML tags from a string
   */
  stripHtml(html: string): string {
    if (!html) return '';
    return html.replace(/<[^>]*>/g, '');
  }

  /**
   * Get color for event card based on individual owner's color
   */
  getEventColor(event: any): string {
    return event.ownerColorHex || '#f59e0b'; // Specific color from the user's settings
  }
}
