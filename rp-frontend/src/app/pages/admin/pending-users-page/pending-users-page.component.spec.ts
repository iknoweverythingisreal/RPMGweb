import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PendingUsersPageComponent } from './pending-users-page.component';

describe('PendingUsersPageComponent', () => {
  let component: PendingUsersPageComponent;
  let fixture: ComponentFixture<PendingUsersPageComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PendingUsersPageComponent]
    });
    fixture = TestBed.createComponent(PendingUsersPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
