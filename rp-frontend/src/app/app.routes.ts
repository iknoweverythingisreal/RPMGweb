import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';
import { roleGuard } from './services/role.guard'; // ✅ เพิ่มบรรทัดนี้
import { LoginComponent } from './pages/login/login.component';
import { CalendarPageComponent } from './pages/calendar-page/calendar-page.component';
import { DashboardPageComponent } from './pages/dashboard/dashboard-page/dashboard-page.component'


export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: 'login', component: LoginComponent },
  {
    path: 'pending-approval',
    loadComponent: () => import('./pages/pending-approval-page/pending-approval-page.component').then(m => m.PendingApprovalPageComponent)
  },

  {
    path: 'calendar',
    component: CalendarPageComponent,
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'EMPLOYEE', 'VISITOR', 'TECHNICAL', 'TECH_LEAD'] }, // ทุก role เข้าดูได้
  },

  // ✅ Select Event Page (Entry point for Inventory Module)
  {
    path: 'inventory/select-event',
    loadComponent: () =>
      import('./pages/select-event-page/select-event-page.component').then(m => m.SelectEventPageComponent),
    title: 'Select Event',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] },
  },

  // ✅ Event-First Inventory Flow (all require eventId)
  {
    path: 'inventory/event/:eventId',
    loadComponent: () =>
      import('./pages/inventory/inventory-page/inventory-page.component').then(m => m.InventoryPageComponent),
    title: 'Equipment Inventory',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] },
  },
  {
    path: 'inventory/event/:eventId/item/:itemId',
    loadComponent: () =>
      import('./pages/inventory/item-detail-page/item-detail-page.component').then(m => m.ItemDetailPageComponent),
    title: 'Item Details',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] },
  },
  {
    path: 'inventory/event/:eventId/cart',
    loadComponent: () => import('./pages/inventory/cart-page/cart-page.component').then(m => m.CartPageComponent),
    title: 'Equipment Cart',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] }
  },
  {
    path: 'inventory/event/:eventId/room-assign',
    loadComponent: () => import('./pages/inventory/room-assignment-page/room-assignment-page.component').then(m => m.RoomAssignmentPageComponent),
    title: 'Room Assignment',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] }
  },

  // ✅ History Routes (MVP Core Flow)
  {
    path: 'history',
    loadComponent: () => import('./pages/history/history-list-page/history-list-page.component').then(m => m.HistoryListPageComponent),
    title: 'Event History',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD', 'EMPLOYEE'] }
  },
  {
    path: 'history/event/:eventId',
    loadComponent: () => import('./pages/history/history-detail-page/history-detail-page.component').then(m => m.HistoryDetailPageComponent),
    title: 'Event Detail',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD', 'EMPLOYEE'] }
  },

  {
    path: 'admin/pending-users',
    loadComponent: () =>
      import('./pages/admin/pending-users-page/pending-users-page.component')
        .then(m => m.PendingUsersPageComponent),
    title: 'Pending Users',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'TECH_LEAD'] }
  },

  {
    path: 'inventory/event/:id',
    loadComponent: () =>
      import('./pages/inventory/event-detail-page/event-detail-page.component')
        .then(m => m.EventDetailPageComponent),
    title: 'Event Details',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] }
  },

  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard-page/dashboard-page.component')
      .then(m => m.DashboardPageComponent),
    title: 'Dashboard',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] },
  },

  {
    path: 'inventory/:eventId/availability',
    loadComponent: () =>
      import('./pages/inventory/equipment-availability-page/equipment-availability-page.component')
        .then(m => m.EquipmentAvailabilityPageComponent),
    title: 'Equipment Availability',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['MANAGER', 'ADMIN'] }
  },

  // ❌ Removed standalone cart route - must go through event context

  {
    path: 'inventory/virtual-storage',
    loadComponent: () =>
      import('./pages/inventory/virtual-storage-page/virtual-storage-page.component')
        .then(m => m.VirtualStoragePageComponent),
    title: 'Virtual Storage',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'TECHNICAL', 'TECH_LEAD'] },
  },

  {
    path: 'replace-items',
    loadComponent: () => import('./pages/inventory/replace-items-page/replace-items-page.component').then(m => m.ReplaceItemsPageComponent),
    title: 'Replace Items',
    canMatch: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER', 'TECHNICAL', 'TECH_LEAD'] }
  },

  { path: '**', redirectTo: 'calendar' }
];
