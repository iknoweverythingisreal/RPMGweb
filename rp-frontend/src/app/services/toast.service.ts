import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface Toast {
  message: string;
  type?: 'success' | 'error' | 'info' | 'warning';
  duration?: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private _toast$ = new BehaviorSubject<Toast | null>(null);
  toast$ = this._toast$.asObservable();

  show(message: string, type: Toast['type'] = 'info', duration = 2500) {
    this._toast$.next({ message, type, duration });
    setTimeout(() => this.clear(), duration);
  }

  clear() {
    this._toast$.next(null);
  }
}
