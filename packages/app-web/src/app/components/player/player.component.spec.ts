import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { PlayerComponent } from './player.component';
import { PlayerModule } from './player.module';
import { AppTestModule } from '../../app-test.module';

describe('BubbleComponent', () => {
  let component: PlayerComponent;
  let fixture: ComponentFixture<PlayerComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [PlayerModule, AppTestModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PlayerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
