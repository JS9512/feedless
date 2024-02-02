import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { SubscriptionEditPage } from './subscription-edit.page';
import { AppTestModule } from '../../../app-test.module';
import { SubscriptionEditPageModule } from './subscription-edit.module';

describe('SubscriptionEditPage', () => {
  let component: SubscriptionEditPage;
  let fixture: ComponentFixture<SubscriptionEditPage>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [SubscriptionEditPageModule, AppTestModule.withDefaults()],
    }).compileComponents();

    fixture = TestBed.createComponent(SubscriptionEditPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
