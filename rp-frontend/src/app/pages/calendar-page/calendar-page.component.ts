// calendar-page.component.ts - Enhanced with Mobile Sidebar
import { Component, OnInit, HostListener, ChangeDetectorRef, HostBinding, ViewChild, ElementRef } from '@angular/core';
import { NgIf, NgFor, CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  EventService,
  CalendarEvent as ApiEvent,
  CreateEventRequest,
  DateTimeHelper,
  CalendarOwner
} from '../../services/event.service';
import { UserService } from '../../services/user.service';
import { Router, ActivatedRoute } from '@angular/router';
import { ToastService } from '../../services/toast.service';
import { EventItemsService } from '../../services/event-items.service';


type Palette = 'white' | 'blue';
type VisualTheme = 'light' | 'dark';

interface CalendarEvent extends ApiEvent {
  start?: Date;
  end?: Date;
  color?: string;
  source: 'INTERNAL' | 'TEAMUP';
  ownerId?: number; owner_id?: number;
  userId?: number; user_id?: number;
  creatorId?: number; creator_id?: number;
  createdById?: number; created_by_id?: number;
  owner?: { id: number; name?: string; displayName?: string;[k: string]: any };
  user?: { id: number; name?: string; displayName?: string;[k: string]: any };
  creator?: { id: number; name?: string; displayName?: string;[k: string]: any };
  ownerName?: string;
  ownerColorHex?: string;
  ownerColor?: string; // Deprecated, use ownerColorHex
  allColors?: string[]; // All participant colors for gradient
  managers?: { id: number; name?: string; displayName?: string;[k: string]: any }[];
  managerIds?: number[];
  techLeadId?: number;
  teamupSubcalendarIds?: number[];
  [key: string]: any;
}

interface Category { id: string; name: string; color: string; visible: boolean; }
interface CalendarCell {
  date: Date;
  isCurrentMonth: boolean;
  isToday: boolean;
  events: CalendarEvent[];
  layout?: (CalendarEvent | 'spacer' | null)[]; // Unified layout slots
}
type UserLight = { id: number; displayName?: string; name?: string; full_name?: string; email?: string; calendarColor?: string; calendar_color?: string; color?: string;[k: string]: any; };
type EventSegment = { event: CalendarEvent; startIdx: number; endIdx: number; level: number; contLeft?: boolean; contRight?: boolean; };
type WeekRow = { start: Date; end: Date; segments: EventSegment[]; };
type SvgBar = {
  id: number;
  x: number;
  y: number;
  w: number;
  h: number;
  color: string;
  title: string;
  contLeft?: boolean;
  contRight?: boolean;
  gradientId?: string; // ID for SVG linearGradient
  patternId?: string; // ID for SVG pattern (stripes)
  isGradient?: boolean;
};
interface PatternDef { id: string; colors: string[] };
type GradientDef = { id: string; stops: { offset: string; color: string }[] };

@Component({
  selector: 'app-calendar-page',
  templateUrl: './calendar-page.component.html',
  styleUrls: ['./calendar-page.component.scss'],
  standalone: true,
  imports: [NgIf, NgFor, FormsModule, CommonModule]
})
export class CalendarPageComponent implements OnInit {
  /* ===== Theme ===== */
  @HostBinding('class') themeClass = 'theme-blue';
  private palette: Palette = (localStorage.getItem('calendarTheme') as Palette) || 'blue';
  theme: VisualTheme = (localStorage.getItem('theme') as VisualTheme) || (this.palette === 'white' ? 'light' : 'dark');

  toggleTheme(): void {
    this.theme = this.theme === 'light' ? 'dark' : 'light';
    localStorage.setItem('theme', this.theme);
    this.palette = this.theme === 'light' ? 'white' : 'blue';
    localStorage.setItem('calendarTheme', this.palette);
    this.applyPalette();
  }

  private applyPalette(): void {
    this.themeClass = `theme-${this.palette}`;
    this.cdr.detectChanges();
    this.scheduleBars();
  }

  /* ===== Enhanced Sidebar / Layout ===== */
  isSidebarOpen = window.innerWidth > 768;
  viewportIsMobile = false;

  toggleSidebar(): void {
    this.isSidebarOpen = !this.isSidebarOpen;

    // Handle body scroll lock on mobile
    if (this.viewportIsMobile) {
      if (this.isSidebarOpen) {
        document.body.style.overflow = 'hidden';
      } else {
        document.body.style.overflow = '';
      }
    } else {
      // Desktop: Trigger resize to redraw SVG bars after transition
      setTimeout(() => {
        this.scheduleBars();
      }, 300); // Match CSS transition duration
    }
    this.cdr.detectChanges();
  }

  closeSidebar(): void {
    this.isSidebarOpen = false;

    // Release body scroll lock
    if (this.viewportIsMobile) {
      document.body.style.overflow = '';
    } else {
      // Desktop: Trigger resize
      setTimeout(() => {
        this.scheduleBars();
      }, 300);
    }
    this.cdr.detectChanges();
  }

  private updateSidebarMode(): void {
    const wasMobile = this.viewportIsMobile;
    this.viewportIsMobile = window.innerWidth <= 768;

    // Desktop: keep sidebar state, Mobile: close by default unless manually opened
    if (!this.viewportIsMobile && wasMobile) {
      // Switching from mobile to desktop -> Open sidebar by default
      document.body.style.overflow = '';
      this.isSidebarOpen = true;
    } else if (this.viewportIsMobile && !wasMobile) {
      // Switching from desktop to mobile -> Close sidebar
      this.isSidebarOpen = false;
      document.body.style.overflow = '';
    }

    // Initial load check
    if (!wasMobile && !this.viewportIsMobile && this.isSidebarOpen === false) {
      // If it was already desktop and closed, keep it closed. 
      // But if this is first run (wasMobile is false initially?), we might want to default to open.
      // Actually, let's just default to open on init if desktop.
    }
  }

  /* ===== Loading ===== */
  isLoading = false;

  /* ===== UI Design Constants ===== */
  readonly DAY_START_HOUR = 6;
  readonly DAY_END_HOUR = 22;
  readonly HOUR_HEIGHT = 60; // pixel per hour
  timeSlots: string[] = [];

  /* ===== Basic State ===== */
  currentView: 'month' | 'week' | 'day' = 'month';

  selectedDate = new Date();
  currentDate = new Date();

  isEditMode = false;
  editingEventId: number | null = null;
  isModalOpen = false;
  /* ===== Drawer State ===== */
  isDrawerOpen = false;
  selectedEvent: CalendarEvent | null = null;


  private currentUserId: number | null = null;
  currentUserName = localStorage.getItem('userName') || localStorage.getItem('userEmail') || 'Me';
  meColor = localStorage.getItem('calendarColor') || '#667eea';
  // userRole removed, use UserService instead

  users: UserLight[] = [];
  selectedUserIds: number[] = [];
  userQuery = '';

  get assignmentUsers(): UserLight[] {
    if (!this.currentUserId) return this.users;
    return this.users.filter(u => Number(u.id) !== this.currentUserId);
  }
  private userColor: Record<number, string> = {};

  owners: CalendarOwner[] = [];
  selectedOwnerIds: number[] = [];

  events: CalendarEvent[] = [];
  calendarCells: CalendarCell[] = [];
  calendarRows: { cells: CalendarCell[] }[] = []; // NEW: Row-based structure
  rowHeights: number[] = []; // NEW: Dynamic heights for each row
  weekRows: WeekRow[] = [];
  weekDays: { date: Date; isToday: boolean; events: CalendarEvent[] }[] = [];

  svgBars: SvgBar[] = [];
  gradientDefs: GradientDef[] = []; // SVG gradient definitions
  patternDefs: PatternDef[] = []; // SVG pattern definitions for stripes
  svgW = 700;
  svgH = 6 * 120;

  isUserMenuOpen = false;
  minDateTime = '';

  @ViewChild('gridWrap', { static: false }) gridWrap?: ElementRef<HTMLDivElement>;
  private rafId: number | null = null;
  private resizeTimer: any;

  form = { title: '', start: '', end: '', category: 'annual_meeting', description: '', location: '' };
  selectedManagerIds: number[] = [];

  categories: Category[] = [
    { id: 'annual_meeting', name: 'Annual meeting', color: '#0ea5e9', visible: true },
    { id: 'product_launch', name: 'Product launch', color: '#10b981', visible: true },
    { id: 'private_party', name: 'Private party', color: '#f59e0b', visible: true },
    { id: 'conference', name: 'Conference', color: '#ef4444', visible: true }
  ];

  constructor(
    private eventService: EventService,
    public userService: UserService,
    private cdr: ChangeDetectorRef,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService,
    private eventItemsService: EventItemsService
  ) { }

  ngOnInit() {
    this.applyPalette();
    this.updateSidebarMode();

    // Try getting from UserService first, then localStorage
    const user = this.userService.currentUser;
    if (user && user.id) {
      this.currentUserId = user.id;
    } else {
      const uid = localStorage.getItem('userId');
      this.currentUserId = uid ? Number(uid) : null;
    }

    // Subscribe to user changes
    this.userService.user$.subscribe(u => {
      if (u && u.id) this.currentUserId = u.id;
    });

    // min  datetime-local
    this.minDateTime = this.getNowLocal();

    this.generateTimeSlots();
    this.loadOwners();
    this.loadUsers();

    // Check for query param to auto-open modal
    this.route.queryParams.subscribe((params: any) => {
      if (params['openModal'] === 'true') {
        // Wait for view to initialize before opening modal
        setTimeout(() => {
          this.showEventModal();
          // Clear the query param after opening
          this.router.navigate([], {
            relativeTo: this.route,
            queryParams: {},
            replaceUrl: true
          });
        }, 300);
      }
    });
  }

  /* ===== Users ===== */
  loadUsers() {
    this.isLoading = true;
    this.eventService.getUsersLight().subscribe({
      next: (users: any[]) => {
        if (!users || users.length === 0) {
          this.setOnlyMe();
          this.loadEventsFromAPI();
          return;
        }
        // Filter to show only MANAGER and ADMIN roles
        let filteredUsers = users.filter(u => {
          const role = (u.role || '').toUpperCase();
          return role === 'MANAGER' || role === 'ADMIN';
        });

        // Fallback: if no managers/admins found, show all users (to avoid empty list bug)
        if (filteredUsers.length === 0) {
          filteredUsers = users;
        }

        this.users = (filteredUsers as UserLight[]).map(u => ({
          ...u,
          displayName: u.displayName || u.name || u.full_name || u.email || `User ${u.id}`
        }));
        this.buildUserColorMap(this.users);
        this.initializeSelectedUsers();
        this.loadEventsFromAPI();
      },
      error: () => {
        this.setOnlyMe();
        this.loadEventsFromAPI();
      }
    });
  }

  loadOwners() {
    this.eventService.getOwners().subscribe({
      next: (owners) => {
        this.owners = owners;
        // Default: show all owners
        this.selectedOwnerIds = owners.map(o => o.id);
      },
      error: (err) => console.error('Failed to load owners', err)
    });
  }

  /* ... (keeping other methods as is, but I can't skip lines in replace_file_content easily if they are far apart) ... */
  /* Actually, I should do two separate edits or one multi_replace if they are far apart. They are far apart (line 173 vs 560). */
  /* I will use multi_replace_file_content. */

  private buildUserColorMap(users: UserLight[]): void {
    this.userColor = {};
    users.forEach(u => {
      const id = Number(u?.id);
      const color = u?.calendarColor || u?.calendar_color || u?.color || '';
      if (Number.isFinite(id) && color?.trim()) this.userColor[id] = color.trim();
    });
  }

  private initializeSelectedUsers(): void {
    const meId = Number(localStorage.getItem('userId'));
    if (this.userService.isManager) {
      // Managers/Admins: Default to "Only Me"
      this.selectedUserIds = Number.isFinite(meId) ? [meId] : [];
    } else {
      // Tech/Employee: Default to "Select All"
      this.selectedUserIds = this.users.map(u => u.id);
    }
  }

  private setOnlyMe(): void {
    const meId = Number(localStorage.getItem('userId'));
    if (Number.isFinite(meId)) {
      this.users = [{ id: meId, displayName: this.currentUserName, calendarColor: this.meColor }];
      this.selectedUserIds = [meId];
      this.userColor = { [meId]: this.meColor };
    } else {
      this.users = [];
      this.selectedUserIds = [];
      this.userColor = {};
    }
  }

  /* ===== Events ===== */
  loadEventsFromAPI(): void {
    this.isLoading = true;

    // Calculate visible range based on current month/view
    const y = this.currentDate.getFullYear();
    const m = this.currentDate.getMonth();

    // We fetch from the start of the padded grid (usually includes some days from prev month)
    const first = new Date(y, m, 1, 12, 0, 0);
    const gridStart = new Date(first);
    gridStart.setDate(first.getDate() - (first.getDay() || 0)); // Start of the week containing the 1st

    const gridEnd = new Date(gridStart);
    gridEnd.setDate(gridStart.getDate() + 42); // 6 weeks of display

    const from = DateTimeHelper.toLocalDate(gridStart);
    const to = DateTimeHelper.toLocalDate(gridEnd);

    console.log(`[DEBUG] Fetching events from ${from} to ${to}`);

    this.eventService.getEventsRange(from, to).subscribe({
      next: (eventsDTO: any[]) => {
        console.log('[DEBUG] Events received:', eventsDTO.length);

        this.events = eventsDTO.map(e => {
          const startDate = DateTimeHelper.combineDateTime(e.startDate, e.startTime ?? '00:00:00');
          const endDate = DateTimeHelper.combineDateTime(e.endDate, e.endTime ?? '00:00:00');

          // Determine event source
          const isTeamup = !!(e.teamupSubcalendarIds && e.teamupSubcalendarIds.length > 0);
          const source = isTeamup ? 'TEAMUP' : 'INTERNAL';

          return {
            ...e,
            source,
            color: e.ownerColor || e.ownerColorHex || this.getCategoryColor(e.customFields?.category || 'annual_meeting'),
            start: isNaN(+startDate) ? undefined : startDate,
            end: isNaN(+endDate) ? undefined : endDate
          };
        });

        if (this.selectedEvent) {
          const updated = this.events.find(ev => ev.id === this.selectedEvent?.id);
          if (updated) this.selectedEvent = updated;
        }

        this.generateCalendar();
        this.isLoading = false;
      },
      error: (err: any) => {
        this.toastService.show('Failed to load events: ' + (err?.error?.message || err.message || 'Unknown error'), 'error');
        this.isLoading = false;
      }
    });
  }

  syncNow(): void {
    this.isLoading = true;
    this.eventService.syncFutureEvents().subscribe({
      next: (res: string) => {
        this.loadEventsFromAPI();
        this.isLoading = false;
        this.toastService.show('Sync completed successfully', 'success');
      },
      error: (err: any) => {
        this.isLoading = false;
        console.error(err);
        this.toastService.show('Sync failed: ' + (err?.error?.message || err.message || 'Unknown error'), 'error');
      }
    });
  }

  private processEvent(apiEvent: any): CalendarEvent {
    const startDate = DateTimeHelper.combineDateTime(apiEvent.startDate, apiEvent.startTime ?? '00:00:00');
    const endDate = DateTimeHelper.combineDateTime(apiEvent.endDate, apiEvent.endTime ?? '00:00:00');
    return { ...apiEvent, start: isNaN(+startDate) ? undefined : startDate, end: isNaN(+endDate) ? undefined : endDate };
  }

  getOwnerId(event: any): number | null {
    const ownerFields = ['ownerId', 'owner_id', 'userId', 'user_id', 'creatorId', 'creator_id', 'createdById', 'created_by_id', 'assignedToId', 'assigned_to_id', 'authorId', 'author_id', 'createdBy', 'created_by'];
    for (const f of ownerFields) {
      const v = event?.[f];
      if (v != null) {
        const id = Number(v);
        if (Number.isFinite(id) && id > 0) return id;
      }
    }
    const nested = [event?.owner?.id, event?.user?.id, event?.creator?.id, event?.createdBy?.id, event?.created_by?.id];
    for (const v of nested) {
      const id = Number(v);
      if (Number.isFinite(id) && id > 0) return id;
    }
    return null;
  }

  getManagerNames(event: CalendarEvent | null): string {
    if (!event) return '';
    const names = new Set<string>();

    const extractName = (u: any) => u?.displayName || u?.name || u?.full_name || u?.username || u?.email;

    // 1. Add Owner/Creator Name (Check all likely objects)
    const ownerNameStr = event.ownerName;
    if (ownerNameStr) names.add(ownerNameStr);

    const ownerObjName = extractName(event.owner) || extractName(event.createdBy) || extractName(event.creator) || extractName(event.user);
    if (ownerObjName) names.add(ownerObjName);

    // 2. Add Manager Names (Populated objects)
    if (event.managers && event.managers.length > 0) {
      event.managers.forEach(m => {
        const name = extractName(m) || `User ${m.id}`;
        if (name) names.add(name);
      });
    }

    // 3. Fallback: Lookup names via managerIds if the objects weren't populated
    if (event.managerIds && event.managerIds.length > 0) {
      event.managerIds.forEach(id => {
        const u = this.users.find(x => x.id === id);
        if (u) {
          const name = extractName(u);
          if (name) names.add(name);
        }
      });
    }

    if (names.size === 0) return 'Unknown';
    return Array.from(names).join(', ');
  }

  getColorForEvent(event: CalendarEvent): string {
    const ownerId = this.getOwnerId(event);
    if (ownerId != null && this.userColor[ownerId]) return this.userColor[ownerId];
    if (event.color && typeof event.color === 'string') return event.color;
    return this.getCategoryColor(event.customFields?.category || 'annual_meeting');
  }

  getCategoryColor(id: string): string {
    return this.categories.find(c => c.id === id)?.color || '#3498db';
  }

  /**
   * Create CSS gradient from multiple colors
   * Colors are distributed equally across the gradient
   */
  private createGradient(colors: string[]): string {
    if (!colors || colors.length === 0) {
      return '#3498db'; // fallback
    }
    if (colors.length === 1) {
      return colors[0]; // single color, no gradient needed
    }

    // ⭐ Hard-stop Gradient: Divide into equal distinct blocks
    const step = 100 / colors.length;
    const stops = colors.map((color, i) => {
      const start = i * step;
      const end = (i + 1) * step;
      // Define color block by setting stops at both start and end percent
      return `${color} ${start}%, ${color} ${end}%`;
    }).join(', ');

    return `linear-gradient(90deg, ${stops})`;
  }

  /**
   * Get color for event bar - returns gradient metadata if multiple owners/managers
   */
  private getEventBarColor(event: CalendarEvent): { color: string; gradientId?: string; patternId?: string; stops?: any[]; colors?: string[] } {
    // Use allColors if available (multi-owner pattern - Striped)
    if (event.allColors && event.allColors.length > 1) {
      const patternId = `pattern-${event.id}`;
      return {
        color: `url(#${patternId})`,
        patternId: patternId,
        colors: event.allColors
      };
    }

    // Single color or fallback
    const singleColor = event.allColors?.[0] || event.ownerColorHex || this.getCategoryColor(event.customFields?.category || 'annual_meeting');
    return { color: singleColor };
  }

  getEventStyle(event: CalendarEvent): any {
    const color = this.getColorForEvent(event);
    const textColor = this.getBarTextColor(color);
    return {
      background: this.hexToRgba(color, 0.15),
      borderLeft: `4px solid ${color}`,
      color: textColor
    };
  }

  private hexToRgba(hex: string, a: number): string {
    const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    if (!m) return `rgba(66,133,244,${a})`;
    const r = parseInt(m[1], 16), g = parseInt(m[2], 16), b = parseInt(m[3], 16);
    return `rgba(${r},${g},${b},${a})`;
  }

  /* ===== Build calendar & overlay ===== */
  generateCalendar(): void {
    this.buildCalendarCells();
    this.buildWeekSegments();
    this.calculateRowHeights(); // NEW: Calculate dynamic heights
    this.scheduleBars();
    this.cdr.detectChanges();
  }

  private buildCalendarCells(): void {
    this.calendarCells = [];
    this.calendarRows = []; // NEW: Reset rows
    const y = this.currentDate.getFullYear();
    const m = this.currentDate.getMonth();

    // Use noon to avoid DST issues during grid generation
    const first = new Date(y, m, 1, 12, 0, 0);
    const start = new Date(first);
    start.setDate(first.getDate() - first.getDay());

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    for (let i = 0; i < 42; i++) {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      d.setHours(0, 0, 0, 0); // Reset to midnight for display

      const isCurrentMonth = d.getMonth() === m;
      const isToday = this.isSameDay(d, today);
      const events = this.getSingleDayEventsForDate(d);
      this.calendarCells.push({ date: d, isCurrentMonth, isToday, events });
    }

    // NEW: Group cells into rows (6 rows x 7 cells)
    for (let row = 0; row < 6; row++) {
      const cells = this.calendarCells.slice(row * 7, (row + 1) * 7);
      this.calendarRows.push({ cells });
    }
  }

  private buildWeekSegments(): void {
    const y = this.currentDate.getFullYear();
    const m = this.currentDate.getMonth();
    const first = new Date(y, m, 1, 12, 0, 0);
    const gridStart = new Date(first);
    gridStart.setDate(first.getDate() - first.getDay());

    this.weekRows = [];
    for (let w = 0; w < 6; w++) {
      const weekStart = new Date(gridStart);
      weekStart.setDate(gridStart.getDate() + w * 7);
      weekStart.setHours(0, 0, 0, 0);

      const weekEnd = new Date(weekStart);
      weekEnd.setDate(weekStart.getDate() + 6);
      weekEnd.setHours(23, 59, 59, 999);

      const allEvents = this.getEventsForWeek(weekStart, weekEnd);
      const segs = this.createEventSegments(allEvents, weekStart, weekEnd);
      const pos = this.positionSegments(segs);
      this.weekRows.push({ start: weekStart, end: weekEnd, segments: pos });
    }
  }

  private getEventsForWeek(weekStart: Date, weekEnd: Date): CalendarEvent[] {
    return this.events.filter(ev => {
      if (!(ev.start instanceof Date)) return false;
      const s = this.toDateOnly(ev.start);
      const e = this.getInclusiveEnd(ev.start, ev.end ?? ev.start);
      const overlap = s <= weekEnd && e >= weekStart;
      return overlap && this.passOwnerAndCategoryFilter(ev);
    });
  }

  private createEventSegments(events: CalendarEvent[], weekStart: Date, weekEnd: Date): EventSegment[] {
    return events.map(ev => {
      const s = this.toDateOnly(ev.start!);
      const e = this.getInclusiveEnd(ev.start!, ev.end ?? ev.start!);
      const startIdx = Math.max(0, this.diffDays(weekStart, s));
      const endIdx = Math.min(6, this.diffDays(weekStart, e));
      return {
        event: ev,
        startIdx,
        endIdx,
        level: 0,
        contLeft: s < weekStart,
        contRight: e > weekEnd
      };
    }).sort((a, b) => (b.endIdx - b.startIdx) - (a.endIdx - a.startIdx));
  }

  private positionSegments(segs: EventSegment[]): EventSegment[] {
    const levels: boolean[][] = [];
    for (const s of segs) {
      let placed = false;
      for (let lvl = 0; lvl < levels.length; lvl++) {
        if (this.canPlaceInLevel(s, lvl, levels)) {
          s.level = lvl;
          this.occupyLevel(s, lvl, levels);
          placed = true;
          break;
        }
      }
      if (!placed) {
        const row = new Array(7).fill(false);
        levels.push(row);
        s.level = levels.length - 1;
        this.occupyLevel(s, s.level, levels);
      }
    }
    return segs;
  }

  // NEW: Calculate dynamic row heights based on event density
  private calculateRowHeights(): void {
    this.rowHeights = [];
    const baseHeight = 100; // Minimum row height in pixels
    const eventHeight = 24; // Height per event level (bar height + gap)
    const topPadding = 35; // Space for date number

    this.weekRows.forEach(wr => {
      // Find the maximum level among all segments in this week
      const maxLevel = wr.segments.reduce((max, seg) =>
        Math.max(max, seg.level), -1);

      // Calculate height: base + space for all event levels + padding
      // Add 1 to maxLevel because levels are 0-indexed
      const neededHeight = topPadding + ((maxLevel + 1) * eventHeight);
      const height = Math.max(baseHeight, neededHeight);

      this.rowHeights.push(height);
    });
  }

  private canPlaceInLevel(seg: EventSegment, lvl: number, levels: boolean[][]): boolean {
    for (let d = seg.startIdx; d <= seg.endIdx; d++)
      if (levels[lvl][d]) return false;
    return true;
  }

  private occupyLevel(seg: EventSegment, lvl: number, levels: boolean[][]): void {
    for (let d = seg.startIdx; d <= seg.endIdx; d++)
      if (levels[lvl]) levels[lvl][d] = true;
  }

  private scheduleBars(): void {
    if (this.rafId) cancelAnimationFrame(this.rafId);
    this.rafId = requestAnimationFrame(() => this.computeSvgBars());
  }

  private computeSvgBars(): void {
    if (this.currentView !== 'month') {
      this.svgBars = [];
      return;
    }

    const host = this.gridWrap?.nativeElement ?? document.querySelector('.calendar-grid-wrap');
    const grid = host?.querySelector('.calendar-grid') as HTMLElement | null;

    if (!grid) {
      this.svgBars = [];
      return;
    }

    const gridRect = grid.getBoundingClientRect();
    this.svgW = Math.round(gridRect.width);
    this.svgH = Math.round(gridRect.height);

    // Get all cells to measure row heights
    const cells = Array.from(grid.querySelectorAll('.calendar-cell')) as HTMLElement[];
    if (cells.length === 0) return;

    // Calculate row heights based on the first cell of each row (every 7th cell)
    const rowTops: number[] = [];
    let currentTop = 0;

    for (let i = 0; i < 6; i++) { // 6 rows
      const cellIndex = i * 7;
      if (cellIndex < cells.length) {
        const cell = cells[cellIndex];
        const h = cell.getBoundingClientRect().height;
        rowTops.push(currentTop);
        currentTop += h;
      }
    }

    const weekNumWidth = this.viewportIsMobile ? 30 : 0;
    const dayWidth = (this.svgW - weekNumWidth) / 7;
    const horizontalPadding = 4;
    const topPadding = 28;
    const barHeight = 20;
    const barGap = 2;

    const bars: SvgBar[] = [];
    const gradients: GradientDef[] = [];
    const patterns: PatternDef[] = [];
    this.weekRows.forEach((wr, wIdx) => {
      // Use actual row top if available, otherwise fallback
      const rTop = rowTops[wIdx] ?? (wIdx * 100);

      wr.segments.forEach(seg => {
        if (seg.event.id == null) return;

        // Teamup style: no horizontal padding if continuing from/to another week
        const padLeft = seg.contLeft ? 0 : horizontalPadding;
        const padRight = seg.contRight ? 0 : horizontalPadding;

        const x = Math.round(weekNumWidth + seg.startIdx * dayWidth + padLeft);
        const width = Math.round((seg.endIdx - seg.startIdx + 1) * dayWidth - padLeft - padRight);

        // Calculate Y relative to the specific row's top
        const y = Math.round(rTop + topPadding + seg.level * (barHeight + barGap));

        const colorInfo = this.getEventBarColor(seg.event) as any;

        // If gradient, add to definitions
        if (colorInfo.gradientId && colorInfo.stops) {
          gradients.push({ id: colorInfo.gradientId, stops: colorInfo.stops });
        }
        // If pattern, add to definitions
        if (colorInfo.patternId && colorInfo.colors) {
          if (!patterns.find(p => p.id === colorInfo.patternId)) {
            patterns.push({ id: colorInfo.patternId, colors: colorInfo.colors });
          }
        }

        // Use only event title for cleaner display
        const title = seg.event.title || seg.event['name'] || 'Event';

        bars.push({
          id: seg.event.id,
          x,
          y,
          w: width,
          h: barHeight,
          color: colorInfo.color,
          title: title,
          contLeft: !!seg.contLeft,
          contRight: !!seg.contRight,
          gradientId: colorInfo.gradientId,
          patternId: colorInfo.patternId,
          isGradient: !!(colorInfo.gradientId || colorInfo.patternId)
        });
      });
    });
    this.svgBars = bars;
    this.gradientDefs = gradients;
    this.patternDefs = patterns;
  }

  /* ===== Utils ===== */
  private toDateOnly(date: Date): Date {
    const r = new Date(date);
    r.setHours(0, 0, 0, 0);
    return r;
  }

  private diffDays(a: Date, b: Date): number {
    const d = 86400000;
    return Math.floor((this.toDateOnly(b).getTime() - this.toDateOnly(a).getTime()) / d);
  }

  private getInclusiveEnd(start: Date, rawEnd?: Date): Date {
    if (!rawEnd) return this.toDateOnly(start);
    const sOnly = this.toDateOnly(start);
    const eOnly = this.toDateOnly(rawEnd);
    const endIsMidnight = rawEnd.getHours() === 0 && rawEnd.getMinutes() === 0 && rawEnd.getSeconds() === 0;
    if (endIsMidnight && eOnly.getTime() > sOnly.getTime()) {
      const adj = new Date(eOnly);
      adj.setDate(adj.getDate() - 1);
      return adj;
    }
    return eOnly;
  }

  isMultiDayEvent(ev: CalendarEvent): boolean {
    if (!(ev.start instanceof Date)) return false;
    const s = this.toDateOnly(ev.start);
    const e = this.getInclusiveEnd(ev.start, ev.end ?? ev.start);
    return this.diffDays(s, e) > 0;
  }

  private passOwnerAndCategoryFilter(ev: CalendarEvent): boolean {
    // 1. Category Filter (Shared)
    const visibleCats = this.categories.filter(c => c.visible).map(c => c.id);
    const catPass = visibleCats.includes(ev.customFields?.category || 'annual_meeting');
    if (!catPass) return false;

    // 2. Distinguish by source to avoid ID collisions
    const isTeamup = ev.source === 'TEAMUP';

    if (isTeamup) {
      // Teamup Filter: Check if the ownerId (Subcalendar) is selected
      if (ev.ownerId != null) {
        return this.selectedOwnerIds.includes(ev.ownerId);
      }
      return true; // Fallback for teamup events without ownerId
    }

    // 3. User Filter (Internal)
    if (this.selectedUserIds.length === 0) return false;

    const ownerId = this.getOwnerId(ev);

    // Check owner
    if (ownerId != null && this.selectedUserIds.includes(ownerId)) {
      return true;
    }
    // Check managers
    if (ev.managers && Array.isArray(ev.managers)) {
      const managerIds = ev.managers.map(m => m.id);
      if (managerIds.some(id => this.selectedUserIds.includes(id))) {
        return true;
      }
    }
    // Check managerIds
    if (ev.managerIds && Array.isArray(ev.managerIds)) {
      if (ev.managerIds.some(id => this.selectedUserIds.includes(id))) {
        return true;
      }
    }

    // Check tech lead
    if (ev.techLeadId != null && this.selectedUserIds.includes(ev.techLeadId)) {
      return true;
    }

    return false;
  }

  /* ===== UI View Helpers (Time Grid) ===== */
  generateTimeSlots(): void {
    this.timeSlots = [];
    for (let i = this.DAY_START_HOUR; i <= this.DAY_END_HOUR; i++) {
      this.timeSlots.push(`${i.toString().padStart(2, '0')}:00`);
    }
  }

  getEventGridStyle(ev: CalendarEvent): any {
    if (!(ev.start instanceof Date)) return {};

    // Fallback end date if missing
    const end = ev.end instanceof Date ? ev.end : new Date(ev.start.getTime() + 60 * 60 * 1000);

    const startMinutes = ev.start.getHours() * 60 + ev.start.getMinutes();
    const endMinutes = end.getHours() * 60 + end.getMinutes();
    const gridStartMinutes = this.DAY_START_HOUR * 60;

    const top = (startMinutes - gridStartMinutes) * (this.HOUR_HEIGHT / 60);
    const duration = Math.max(30, endMinutes - startMinutes);
    const height = duration * (this.HOUR_HEIGHT / 60);

    const color = this.getColorForEvent(ev);

    return {
      'top': `${top}px`,
      'height': `${height}px`,
      'background-color': this.hexToRgba(color, 0.2),
      'border-left': `4px solid ${color}`,
      'color': '#fff', // Or call getBarTextColor(color) if available
      'position': 'absolute',
      'left': '2px',
      'right': '2px',
      'z-index': '10',
      'border-radius': '4px',
      'padding': '4px',
      'font-size': '0.75rem',
      'overflow': 'hidden',
      'box-shadow': '0 2px 4px rgba(0,0,0,0.1)'
    };
  }

  getEventsForDay(date: Date): CalendarEvent[] {
    return this.events.filter(ev => {
      if (!(ev.start instanceof Date)) return false;
      return this.isSameDay(ev.start, date) && this.passOwnerAndCategoryFilter(ev);
    });
  }

  private getSingleDayEventsForDate(date: Date): CalendarEvent[] {
    return this.events.filter(ev => {
      if (!(ev.start instanceof Date)) return false;
      const isSingle = !this.isMultiDayEvent(ev);
      const sameDay = this.isSameDay(ev.start, date);
      const passesFilter = typeof this.passOwnerAndCategoryFilter === 'function'
        ? this.passOwnerAndCategoryFilter(ev)
        : true;

      return sameDay && isSingle && passesFilter;
    });
  }

  private handleOpenEvent(id: number, e: Event): void {
    e.stopPropagation();
    const ev = this.events.find(x => x.id === id);
    if (!ev) {
      console.warn("openEvent(): event not found for id", id);
      return;
    }
    this.openDrawer(ev);
  }


  isSameDay(a: Date, b: Date): boolean {
    return a.toDateString() === b.toDateString();
  }

  truncateText(t: string, n: number): string {
    return t.length > n ? t.slice(0, n) + '...' : t;
  }

  getWeekNumber(d: Date): number {
    const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const dayNum = date.getUTCDay() || 7;
    date.setUTCDate(date.getUTCDate() + 4 - dayNum);
    const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil((((date.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
    return weekNo;
  }

  /** ===== Day View Logic ===== */
  generateDayView(): void {
    const filtered = this.events.filter(ev => {
      if (!(ev.start instanceof Date)) return false;
      const onDay = this.isSameDay(ev.start, this.currentDate) ||
        (ev.end instanceof Date && this.isSameDay(ev.end, this.currentDate));
      return onDay && this.passOwnerAndCategoryFilter(ev);
    });

    this.filteredEvents = filtered.sort(
      (a, b) => (a.start?.getTime() || 0) - (b.start?.getTime() || 0)
    );
    this.cdr.detectChanges();
  }

  isEventInHour(ev: CalendarEvent, hour: number): boolean {
    if (!(ev.start instanceof Date)) return false;
    const h = ev.start.getHours();
    return h === hour;
  }

  /** ===== Week View ===== */
  generateWeekView(): void {
    const current = new Date(this.currentDate);
    const startOfWeek = new Date(current);
    startOfWeek.setDate(current.getDate() - current.getDay());
    const days: { date: Date; isToday: boolean; events: CalendarEvent[] }[] = [];
    const today = new Date();

    for (let i = 0; i < 7; i++) {
      const d = new Date(startOfWeek);
      d.setDate(startOfWeek.getDate() + i);
      const isToday = d.toDateString() === today.toDateString();
      const events = this.events.filter(ev => {
        if (!(ev.start instanceof Date)) return false;
        const sameDay = this.isSameDay(ev.start, d);
        return sameDay && this.passOwnerAndCategoryFilter(ev);
      });
      days.push({ date: d, isToday, events });
    }

    this.weekDays = days;
    this.cdr.detectChanges();
  }

  getCurrentWeekRangeText(): string {
    const start = new Date(this.currentDate);
    start.setDate(start.getDate() - start.getDay());
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    const fmt: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' };
    return `${start.toLocaleDateString(undefined, fmt)} - ${end.toLocaleDateString(undefined, fmt)}`;
  }

  /* ===== User filter ===== */
  isUserVisible(u: UserLight): boolean {
    const q = (this.userQuery || '').trim().toLowerCase();
    if (!q) return true;
    const src = (u.displayName || u.name || u.full_name || u.email || '').toLowerCase();
    return src.includes(q);
  }

  get filteredUsers(): UserLight[] {
    const q = (this.userQuery || '').trim().toLowerCase();
    if (!q) return [];
    return this.users.filter(u => (u.displayName || u.name || u.full_name || u.email || '').toLowerCase().includes(q)).slice(0, 8);
  }

  onUserQueryChange(): void {
    this.cdr.detectChanges();
  }

  onUserSearchKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter') {
      e.preventDefault();
      const top = this.filteredUsers[0];
      if (top) this.toggleUser(top.id);
    } else if (e.key === 'Escape') {
      (e.target as HTMLInputElement).blur();
      this.userQuery = '';
    }
  }

  toggleUser(userId: number): void {
    this.selectedUserIds ??= [];
    if (this.selectedUserIds.includes(userId)) {
      this.selectedUserIds = this.selectedUserIds.filter(id => id !== userId);
    } else {
      this.selectedUserIds.push(userId);
    }
    this.refreshCalendar();
  }

  toggleOwner(ownerId: number): void {
    if (this.selectedOwnerIds.includes(ownerId)) {
      this.selectedOwnerIds = this.selectedOwnerIds.filter(id => id !== ownerId);
    } else {
      this.selectedOwnerIds.push(ownerId);
    }
    this.refreshCalendar();
  }

  selectAllOwners(): void {
    this.selectedOwnerIds = this.owners.map(o => o.id);
    this.refreshCalendar();
  }

  clearAllOwners(): void {
    // Clear = show all (as recommended)
    this.selectedOwnerIds = this.owners.map(o => o.id);
    this.refreshCalendar();
  }

  private refreshCalendar(): void {
    if (this.currentView === 'week') this.generateWeekView();
    else if (this.currentView === 'day') this.generateDayView();
    else this.generateCalendar();
  }

  applyCategoryFilter(category: string): void {
    this.filteredEvents = this.events.filter(ev =>
      ev['category'] === category || category === 'ALL'
    );
    this.generateCalendar();
  }

  filteredEvents: any[] = [];

  applyUserFilter(): void {
    const selectedIds = this.selectedUserIds || [];
    this.filteredEvents = this.events.filter(ev =>
      selectedIds.length === 0 || selectedIds.includes(ev.createdById ?? -1)
    );
    this.generateCalendar();
  }


  selectAllUsers(): void {
    this.selectedUserIds = this.users.map(u => u.id);
    if (this.currentView === 'week') this.generateWeekView();
    else this.generateCalendar();
  }

  clearAllUsers(): void {
    this.selectedUserIds = [];
    if (this.currentView === 'week') this.generateWeekView();
    else this.generateCalendar();
  }

  showOnlyMe(): void {
    const me = Number(localStorage.getItem('userId'));
    if (!Number.isFinite(me)) return;
    const onlyMe = this.selectedUserIds.length === 1 && this.selectedUserIds[0] === me;
    this.selectedUserIds = onlyMe ? [] : [me];
    if (this.currentView === 'week') this.generateWeekView();
    else this.generateCalendar();
  }
  toggleCategory(id: string): void {
    const cat = this.categories.find(c => c.id === id);
    if (!cat) return;
    cat.visible = !cat.visible;
    this.generateCalendar();
    if (this.currentView === 'week') this.generateWeekView();
  }


  /* ===== Navigation ===== */
  getCurrentPeriodText(): string {
    const opt: Intl.DateTimeFormatOptions =
      this.currentView === 'month' ? { year: 'numeric', month: 'long' } :
        this.currentView === 'week' ? { month: 'short', day: 'numeric' } :
          { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
    return this.currentDate.toLocaleDateString('en-US', opt);
  }

  setView(v: 'month' | 'week' | 'day'): void {
    this.currentView = v;
    if (v === 'day') this.generateDayView();
    else if (v === 'week') this.generateWeekView();
    else this.generateCalendar();
  }


  onViewChange(e: Event): void {
    this.setView((e.target as HTMLSelectElement).value as any);
  }

  goToToday(): void {
    this.currentDate = new Date();
    this.selectedDate = new Date();
    this.loadEventsFromAPI();
  }

  previousPeriod(): void {
    const d = new Date(this.currentDate);
    if (this.currentView === 'month')
      d.setMonth(d.getMonth() - 1);
    else if (this.currentView === 'week')
      d.setDate(d.getDate() - 7);
    else
      d.setDate(d.getDate() - 1);

    this.currentDate = d;
    this.loadEventsFromAPI();
  }

  nextPeriod(): void {
    const d = new Date(this.currentDate);
    if (this.currentView === 'month')
      d.setMonth(d.getMonth() + 1);
    else if (this.currentView === 'week')
      d.setDate(d.getDate() + 7);
    else
      d.setDate(d.getDate() + 1);

    this.currentDate = d;
    this.loadEventsFromAPI();
  }


  /* ===== Modal ===== */
  get canCreateEvent(): boolean {
    return this.userService.isManager || this.userService.isTechLead;
  }

  showEventModal(): void {
    if (!this.canCreateEvent) {
      this.toastService.show('You do not have permission to create events', 'error');
      return;
    }
    this.isModalOpen = true;
    this.isEditMode = false;
    this.editingEventId = null;
    this.form = { title: '', start: '', end: '', category: 'annual_meeting', description: '', location: '' };
    this.selectedManagerIds = [];
  }


  closeModal(): void {
    const wasEdit = this.isEditMode;
    this.isModalOpen = false;
    // If we were editing, reopen the drawer to show event details
    if (wasEdit && this.selectedEvent) {
      this.isDrawerOpen = true;
    }
  }

  openEvent(id: number | undefined, e: Event): void {
    if (!id) {
      console.warn("openEvent(): id is undefined");
      return;
    }
    this.handleOpenEvent(id, e);
  }

  isFormValid(): boolean {
    const { title, start, end } = this.form;
    if (!title?.trim()) return false;
    if (!start || !end) return false;
    const s = new Date(start);
    const ed = new Date(end);
    if (ed <= s) return false;
    return true;
  }

  saveEvent(e?: Event): void {
    e?.preventDefault?.();
    const { title, start, end, category, description, location } = this.form;

    if (!this.userService.isManager) {
      this.toastService.show(' Only MANAGER or ADMIN can create/edit events', 'error');
      return;
    }

    if (!this.isFormValid()) {
      this.toastService.show(' Please fill in all required fields correctly', 'error');
      return;
    }

    if (!this.currentUserId) {
      this.toastService.show(' User session lost. Please login again.', 'error');
      return;
    }

    const s = new Date(start);
    const ed = new Date(end);

    const payload: CreateEventRequest = {
      title: title.trim(),
      description: (description ?? '').trim(),
      location: (location ?? '').trim(),
      startDate: DateTimeHelper.toLocalDate(s),
      endDate: DateTimeHelper.toLocalDate(ed),
      startTime: DateTimeHelper.toLocalTimeSeconds(s),
      endTime: DateTimeHelper.toLocalTimeSeconds(ed),
      ownerId: this.currentUserId,
      managerIds: this.selectedManagerIds.length > 0 ? this.selectedManagerIds : undefined,
      customFields: { category }
    };
    const op = this.isEditMode && this.editingEventId
      ? this.eventService.updateEvent(this.editingEventId, payload)
      : this.eventService.createEvent(payload);
    op.subscribe({
      next: () => {
        this.toastService.show(' Event saved successfully', 'success');
        this.loadEventsFromAPI();
        this.closeModal();
      },
      error: (err: any) => {
        const a = this.isEditMode ? '' : '';
        this.toastService.show(`${a}: ` + (err?.error?.message || err.message || 'unknown'), 'error');
      }
    });
  }

  deleteEvent(): void {
    const idToDelete = this.editingEventId || this.selectedEvent?.id;
    if (idToDelete && confirm(' event ?')) {
      this.eventService.deleteEvent(idToDelete).subscribe({
        next: () => {
          this.loadEventsFromAPI();
          this.closeModal();
          this.closeDrawer(); // Also close drawer if open
        },
        error: (err: any) => {
          this.toastService.show('Failed to delete event: ' + (err?.error?.message || err.message || 'Unknown error'), 'error');
        }
      });
    }
  }

  /* ===== Event Drawer / Modal Details ===== */
  bookedItems: any[] = [];
  groupedItems: { category: string, items: any[], isOpen: boolean }[] = [];
  isItemsLoading = false;

  openDrawer(ev: CalendarEvent): void {
    this.selectedEvent = ev;
    this.isDrawerOpen = true;
    document.body.style.overflow = 'hidden';
    this.loadEventItems(ev.id);
  }

  loadEventItems(eventId: number): void {
    if (!eventId) return;
    this.isItemsLoading = true;
    this.bookedItems = [];
    this.groupedItems = [];

    this.eventItemsService.getEventItems(eventId).subscribe({
      next: (items) => {
        this.bookedItems = items;
        this.groupItemsByCategory(items);
        this.isItemsLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading items:', err);
        this.isItemsLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private groupItemsByCategory(items: any[]): void {
    const groups: { [key: string]: any[] } = {};
    items.forEach(item => {
      const cat = item.item?.category || item.category || 'Other / ';
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(item);
    });

    this.groupedItems = Object.keys(groups).map(cat => ({
      category: cat,
      items: groups[cat],
      isOpen: false // Default to closed
    }));

    // Auto-open first group if only one
    if (this.groupedItems.length === 1) {
      this.groupedItems[0].isOpen = true;
    }
  }

  get warehouseItems() {
    return this.bookedItems.filter(it => Number(it.allocatedQuantity || 0) > 0);
  }

  get rentalItems() {
    return this.bookedItems.filter(it => Number(it.allocatedQuantity || 0) === 0);
  }

  get warehouseGrouped() {
    return this.groupItemsByRoom(this.warehouseItems);
  }

  get rentalGrouped() {
    return this.groupItemsByRoom(this.rentalItems);
  }

  get totalItemsCount(): number {
    return this.bookedItems.reduce((acc, it) => acc + (Number(it.requestedQuantity || it.allocatedQuantity || 0)), 0);
  }

  private groupItemsByRoom(items: any[]) {
    const groups = new Map<string, any[]>();

    items.forEach(it => {
      const roomName = it.room || 'Unassigned';
      if (!groups.has(roomName)) {
        groups.set(roomName, []);
      }
      groups.get(roomName)?.push(it);
    });

    return Array.from(groups.entries()).map(([roomName, items]) => {
      return { roomName, items };
    }).sort((a, b) => {
      if (a.roomName === 'Unassigned') return 1;
      if (b.roomName === 'Unassigned') return -1;
      return a.roomName.localeCompare(b.roomName);
    });
  }

  toggleItemGroup(group: any): void {
    group.isOpen = !group.isOpen;
  }

  closeDrawer(): void {
    this.isDrawerOpen = false;
    this.selectedEvent = null;
    this.bookedItems = [];
    this.groupedItems = [];
    document.body.style.overflow = '';
  }

  printWarehouseReport(): void {
    if (!this.selectedEvent?.id) return;
    // Navigate to the history detail page with an auto-print trigger
    this.router.navigate(['/history/event', this.selectedEvent.id], {
      queryParams: { autoPrint: 'true', returnUrl: '/calendar' }
    });
  }

  canEditSelected(): boolean {
    if (this.isPastEvent()) return false;
    return this.userService.isManager; // Or check ownership if needed
  }

  editEvent(): void {
    if (!this.selectedEvent?.id) return;

    // Populate form with selected event data
    const ev = this.selectedEvent;
    this.isEditMode = true;
    this.editingEventId = ev.id || null;

    // Format dates for datetime-local input (YYYY-MM-DDTHH:mm)
    const toLocalISO = (d: Date) => {
      const pad = (n: number) => n < 10 ? '0' + n : n;
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
        'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    };

    this.form = {
      title: ev.title,
      start: ev.start ? toLocalISO(new Date(ev.start)) : '',
      end: ev.end ? toLocalISO(new Date(ev.end)) : '',
      category: ev.customFields?.category || 'annual_meeting',
      description: ev.description || '',
      location: ev.location || ''
    };

    // Set managers
    this.selectedManagerIds = [];
    if (ev.managers && Array.isArray(ev.managers)) {
      this.selectedManagerIds = ev.managers.map(m => m.id);
    } else if (ev.managerIds && Array.isArray(ev.managerIds)) {
      this.selectedManagerIds = ev.managerIds;
    }

    this.isModalOpen = true;
    this.isDrawerOpen = false;
  }

  openEquipmentPage(): void {
    if (!this.selectedEvent?.id || this.isPastEvent()) return;
    // Navigate directly to the inventory page for this specific event
    this.router.navigate(['/inventory/event', this.selectedEvent.id]);
    this.closeDrawer();
  }

  isPastEvent(): boolean {
    if (!this.selectedEvent?.end) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const end = new Date(this.selectedEvent.end);
    end.setHours(23, 59, 59, 999);
    return end < today;
  }

  /* ===== Interaction ===== */
  showMoreEvents(dateStr: string, e: Event): void {
    e.stopPropagation();
    // Show count as toast
    this.toastService.show(`${this.getSingleDayEventsForDate(new Date(dateStr)).length} event(s) on this date`, 'info');
  }

  onCellClick(e: MouseEvent | KeyboardEvent, cell: CalendarCell): void {
    (e as any)?.stopPropagation?.();
    this.selectedDate = cell.date;
  }

  onCellKeydown(e: KeyboardEvent, cell: CalendarCell): void {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      this.onCellClick(e, cell);
    }
  }

  onEventKeydown(e: KeyboardEvent, ev: CalendarEvent): void {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (ev.id != null) this.openEvent(ev.id, e as any);
    }
  }

  onSvgBarKeydown(e: KeyboardEvent, id: number): void {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      this.openEvent(id, e as any);
    }
  }

  /* ===== Tooltip/ARIA & colors ===== */
  getEventTooltip(ev: CalendarEvent): string {
    if (!(ev.start instanceof Date)) return ev.title;
    const s = ev.start, e = this.getInclusiveEnd(ev.start, ev.end ?? ev.start);
    const dOpt: Intl.DateTimeFormatOptions = { year: 'numeric', month: 'short', day: 'numeric' };
    const tOpt: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' };
    return this.isMultiDayEvent(ev)
      ? `${ev.title}  ${s.toLocaleDateString(undefined, dOpt)}  ${e.toLocaleDateString(undefined, dOpt)}`
      : `${ev.title}  ${s.toLocaleDateString(undefined, dOpt)} ${s.toLocaleTimeString(undefined, tOpt)}`;
  }

  getEventAriaLabel(ev: CalendarEvent): string {
    return this.getEventTooltip(ev);
  }

  getBarTextColor(hex?: string): string {
    if (!hex) return '#fff';
    const h = hex.replace('#', '');
    const to = (s: string) => parseInt(s, 16);
    let r: number, g: number, b: number;
    if (h.length === 3) {
      r = to(h[0] + h[0]);
      g = to(h[1] + h[1]);
      b = to(h[2] + h[2]);
    } else {
      r = to(h.slice(0, 2));
      g = to(h.slice(2, 4));
      b = to(h.slice(4, 6));
    }
    const L = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return L > 0.6 ? '#111' : '#fff';
  }

  /* ===== Export ===== */
  exportCalendar(): void {
    try {
      const payload = {
        events: this.events,
        categories: this.categories,
        exportDate: new Date().toISOString()
      };
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `calendar-export-${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(a.href);
    } catch {
      this.toastService.show('Failed to export calendar', 'error');
    }
  }

  /* ===== TrackBy ===== */
  trackByUser = (_: number, u: UserLight) => u.id;
  trackByCategory = (_: number, c: Category) => c.id;
  trackByEvent = (_: number, e: CalendarEvent) => e.id;
  trackByWeek = (_: number, w: WeekRow) => w.start.toISOString();
  trackBySegment = (_: number, s: EventSegment) => `${s.event.id}-${s.level}-${s.startIdx}-${s.endIdx}`;
  trackByCell = (_: number, c: CalendarCell) => c.date.toISOString();
  trackBySvgBar = (_: number, b: SvgBar) => `${b.id}-${b.x}-${b.y}-${b.w}-${b.h}-${b.color}`;

  /* ===== Window / Document events ===== */
  @HostListener('window:resize')
  onResize(): void {
    clearTimeout(this.resizeTimer);
    this.resizeTimer = setTimeout(() => {
      this.updateSidebarMode();
      this.cdr.detectChanges();
      this.scheduleBars();
    }, 150);
  }

  @HostListener('document:click', ['$event'])
  onDocClick(e: Event): void {
    const t = e.target as HTMLElement;

    // Close user menu when clicking outside
    if (this.isUserMenuOpen && !t.closest('.user-dropdown')) {
      this.isUserMenuOpen = false;
    }

    // Close sidebar when clicking outside on mobile
    if (this.isSidebarOpen && this.viewportIsMobile &&
      !t.closest('.sidebar') && !t.closest('.menu-toggle')) {
      this.closeSidebar();
    }
  }

  /* ===== Misc ===== */
  private getNowLocal(): string {
    const n = new Date();
    n.setMinutes(n.getMinutes() - n.getTimezoneOffset());
    return n.toISOString().slice(0, 16);
  }

  onStartDateChange(): void {
    if (!this.form.start) return;
    if (this.form.end && new Date(this.form.end) <= new Date(this.form.start))
      this.form.end = '';
  }

  toggleUserMenu(): void {
    this.isUserMenuOpen = !this.isUserMenuOpen;
  }

  navigateToProfile(): void {
    // console.log('Profile/Settings');
    this.isUserMenuOpen = false;
  }

  navigateToEquipment(): void {
    this.router.navigate(['/inventory/select-event']);
  }

  navigateToHistory(): void {
    this.router.navigate(['/history']);
  }

  navigateToAdmin(): void {
    this.router.navigate(['/admin/pending-users']);
  }

  navigateToReplace(): void {
    this.router.navigate(['/replace-items']);
  }

  logout(): void {
    const keepTheme = localStorage.getItem('theme');
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('userName');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('calendarColor');
    if (keepTheme) localStorage.setItem('theme', keepTheme);
    window.location.href = '/login';
  }

  // ===== Role helpers =====
  get isAdmin(): boolean {
    return this.userService.isAdmin;
  }

  get isManager(): boolean {
    return this.userService.isManager;
  }

  get isTechLead(): boolean {
    return this.userService.isTechLead;
  }

  get isTechMember(): boolean {
    return this.userService.isTechnical && !this.userService.isTechLead;
  }

  get isTech(): boolean {
    return this.userService.isTechnical;
  }

  get isViewer(): boolean {
    return !this.userService.isManager && !this.userService.isTechnical;
  }

  markAsPrepared(eventId: number | undefined): void {
    if (!eventId) return;
    const userId = Number(localStorage.getItem('userId'));
    this.eventService.markPrepared(eventId, userId).subscribe({
      next: () => this.toastService.show('Items marked as READY', 'success'),
      error: err => this.toastService.show('Failed: ' + (err?.error?.message || err.message), 'error')
    });
  }

  markAsChecked(eventId: number | undefined): void {
    if (!eventId) return;
    const userId = Number(localStorage.getItem('userId'));
    this.eventService.markChecked(eventId, userId).subscribe({
      next: () => this.toastService.show('Items marked as CHECKED', 'success'),
      error: err => this.toastService.show('Failed: ' + (err?.error?.message || err.message), 'error')
    });
  }


  private resizeObserver: ResizeObserver | null = null;

  ngAfterViewInit(): void {
    if (this.gridWrap?.nativeElement) {
      this.resizeObserver = new ResizeObserver(() => {
        this.scheduleBars();
      });
      this.resizeObserver.observe(this.gridWrap.nativeElement);
    }
  }

  ngOnDestroy(): void {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
    }
  }

  confirmEventItems(eventId: number | undefined): void {
    if (!eventId) return;
    const userId = Number(localStorage.getItem('userId'));
    this.eventService.confirmEventItems(eventId, userId).subscribe({
      next: () => this.toastService.show('Event confirmed by Manager', 'success'),
      error: err => this.toastService.show('Failed: ' + (err?.error?.message || err.message), 'error')
    });
  }

  // Toggle manager selection for multi-user events
  toggleManagerSelection(userId: number): void {
    const index = this.selectedManagerIds.indexOf(userId);
    if (index > -1) {
      this.selectedManagerIds.splice(index, 1);
    } else {
      this.selectedManagerIds.push(userId);
    }
  }

  isManagerSelected(userId: number): boolean {
    return this.selectedManagerIds.includes(userId);
  }


}


