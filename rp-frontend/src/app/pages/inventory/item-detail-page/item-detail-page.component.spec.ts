import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ItemDetailPageComponent } from './item-detail-page.component';

describe('ItemDetailPageComponent', () => {
  let component: ItemDetailPageComponent;
  let fixture: ComponentFixture<ItemDetailPageComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ItemDetailPageComponent]
    });
    fixture = TestBed.createComponent(ItemDetailPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
