import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { UnifiedAvailabilityService } from '../../../services/unified-availability.service';
import { UnifiedAvailabilityDTO } from '../../../core/models/unified-availability.model';
import { LinkUnitsRequest } from '../../../core/models/serial.model';
import { SerialOpsService } from '../../../services/serial-ops.service';
import { EventBusService } from '../../../services/event-bus.service';
import { ToastService } from '../../../services/toast.service';
import { environment } from 'src/environments/environment';

interface ItemAvailability {
  itemId: number;
  itemName: string;
  category: string;
  uom: string;
  totalQuantity: number;
  allocated: number;
  available: number;
}

@Component({
  selector: 'app-virtual-storage-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './virtual-storage-page.component.html',
  styleUrls: ['./virtual-storage-page.component.scss']
})
export class VirtualStoragePageComponent implements OnInit {

  items = signal<ItemAvailability[]>([]);
  isLoading = signal<boolean>(false);
  selectedSerials: number[] = [];

  /** eventItemId ที่ตรงกับ selectedItemId */
  eventItemIdForThisItem?: number;

  /** วันที่ */
  startDate: string = new Date().toISOString().slice(0, 10);
  endDate: string = new Date().toISOString().slice(0, 10);

  /** unified availability (serial + qty) */
  selectedAvailability?: UnifiedAvailabilityDTO;
  selectedItemId?: number;

  /** FIXED EVENT ID (ภายหลังจะรับมาจาก Event-detail หรือ Global Store) */
  selectedEventId: number = 1;

  /** แผนที่ itemId -> eventItemId */
  eventItems: { itemId: number; eventItemId: number }[] = [];

  constructor(
    private http: HttpClient,
    private unifiedService: UnifiedAvailabilityService,
    private serialOpsService: SerialOpsService,
    private eventBus: EventBusService,
    private toastService: ToastService
  ) { }

  ngOnInit() {
    this.loadEventItemsForThisEvent();   // 🔥 สำคัญที่สุด เพิ่ม mapping itemId → eventItemId
    this.loadAvailability();
  }

  /** โหลด event-items สำหรับ event นี้ */
  loadEventItemsForThisEvent() {
    this.http.get<any[]>(`${environment.apiUrl}/api/event-items/event/${this.selectedEventId}`)
      .subscribe({
        next: res => {
          this.eventItems = res.map(it => ({
            itemId: it.itemId,
            eventItemId: it.id
          }));
        },
        error: err => {
          console.error("❌ Failed to load eventItems", err);
        }
      });
  }

  /** โหลด Availability ของ Virtual Storage */
  loadAvailability() {
    this.isLoading.set(true);

    this.http.get<ItemAvailability[]>(
      `${environment.apiUrl}/api/inventory/availability?startDate=${this.startDate}&endDate=${this.endDate}`
    ).subscribe({
      next: (res) => {
        this.items.set(res);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.toastService.show('Failed to load availability: ' + (err?.error?.message || err.message), 'error');
        this.isLoading.set(false);
      }
    });
  }

  syncVirtualStorage() {
    if (!confirm('Sync virtual storage now?')) return;
    this.isLoading.set(true);

    this.http.post(
      `${environment.apiUrl}/api/inventory/virtual-sync?startDate=${this.startDate}&endDate=${this.endDate}`,
      {}
    ).subscribe({
      next: () => {
        this.toastService.show('Virtual storage synced successfully', 'success');
        this.loadAvailability();
      },
      error: (err) => {
        this.toastService.show('Failed to sync: ' + (err?.error?.message || err.message), 'error');
        this.isLoading.set(false);
      }
    });
  }

  getAvailClass(avail: number, total: number): string {
    if (avail <= 0) return 'status-danger';
    if (avail < total * 0.3) return 'status-warning';
    return 'status-ok';
  }

  /** โหลด detailed availability ของ item เดี่ยว */
  loadItemAvailability(itemId: number) {
    this.selectedItemId = itemId;

    // 🔥 MAP ITEM → eventItemId
    const found = this.eventItems.find(e => e.itemId === itemId);
    this.eventItemIdForThisItem = found?.eventItemId;

    if (!this.eventItemIdForThisItem) {
      console.warn("❌ No eventItemId found for itemId:", itemId);
    }

    this.unifiedService.getFullAvailability(
      itemId,
      this.selectedEventId,
      this.startDate,
      this.endDate
    ).subscribe({
      next: (res) => {
        this.selectedAvailability = res;
      },
      error: (err) => {
        console.error(err);
        this.toastService.show('Failed to load item availability', 'error');
      }
    });
  }

  toggleSerialSelection(unitId: number, checked: boolean) {
    if (checked) {
      this.selectedSerials.push(unitId);
    } else {
      this.selectedSerials = this.selectedSerials.filter(id => id !== unitId);
    }
  }

  /** ส่ง serials เข้า event-item */
  submitSerials() {
    if (!this.eventItemIdForThisItem) {
      this.toastService.show('Missing event item ID', 'error');
      return;
    }

    const req: LinkUnitsRequest = {
      unitIds: this.selectedSerials,
      note: 'picked from virtual storage'
    };

    this.serialOpsService
      .linkUnits(this.selectedEventId, this.eventItemIdForThisItem, req)
      .subscribe({
        next: () => {
          this.toastService.show('Serials linked successfully', 'success');

          this.eventBus.emit('serial-linked', {
            itemId: this.selectedItemId,
            eventItemId: this.eventItemIdForThisItem
          });

          this.selectedSerials = [];
          this.loadItemAvailability(this.selectedItemId!);
        },
        error: (err) => {
          this.toastService.show('Failed to link serials', 'error');
          console.error(err);
        }
      });
  }


  get loading() {
    return this.isLoading;
  }

}
