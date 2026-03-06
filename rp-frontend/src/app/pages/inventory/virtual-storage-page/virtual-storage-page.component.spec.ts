import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VirtualStoragePageComponent } from './virtual-storage-page.component';

describe('VirtualStoragePageComponent', () => {
  let component: VirtualStoragePageComponent;
  let fixture: ComponentFixture<VirtualStoragePageComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VirtualStoragePageComponent]
    });
    fixture = TestBed.createComponent(VirtualStoragePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
