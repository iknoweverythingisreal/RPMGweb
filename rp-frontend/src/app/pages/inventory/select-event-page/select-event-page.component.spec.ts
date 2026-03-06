import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SelectEventPageComponent } from './select-event-page.component';

describe('SelectEventPageComponent', () => {
  let component: SelectEventPageComponent;
  let fixture: ComponentFixture<SelectEventPageComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SelectEventPageComponent]
    });
    fixture = TestBed.createComponent(SelectEventPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
