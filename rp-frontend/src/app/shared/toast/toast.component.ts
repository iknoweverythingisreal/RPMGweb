import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, Toast } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div *ngIf="toast" class="toast" [class]="toast.type">
    <span>{{ toast.message }}</span>
  </div>
  `,
  styles: [`
    .toast {
      position: fixed;
      bottom: 24px;
      right: 24px;
      padding: 12px 18px;
      border-radius: 6px;
      color: white;
      font-weight: 500;
      animation: fadeInOut 3s ease forwards;
      z-index: 9999;
    }
    .toast.success { background: #16a34a; }
    .toast.error { background: #dc2626; }
    .toast.info { background: #2563eb; }
    .toast.warning { background: #f59e0b; }

    @keyframes fadeInOut {
      0% { opacity: 0; transform: translateY(20px); }
      10%, 90% { opacity: 1; transform: translateY(0); }
      100% { opacity: 0; transform: translateY(20px); }
    }
  `]
})
export class ToastComponent implements OnInit {
  toast: Toast | null = null;
  constructor(private toastService: ToastService) {}

  ngOnInit() {
    this.toastService.toast$.subscribe(t => this.toast = t);
  }
}
