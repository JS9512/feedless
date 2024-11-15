import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { PcTrackerProductPage } from './pc-tracker-product.page';
import {
  ApolloMockController,
  AppTestModule,
  mockScrape,
  mockServerSettings,
} from '../../app-test.module';
import { PcTrackerProductModule } from './pc-tracker-product.module';
import { RouterTestingModule } from '@angular/router/testing';
import { ServerConfigService } from '../../services/server-config.service';
import { ApolloClient } from '@apollo/client/core';

describe('PcTrackerProductPage', () => {
  let component: PcTrackerProductPage;
  let fixture: ComponentFixture<PcTrackerProductPage>;

  beforeEach(waitForAsync(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PcTrackerProductModule,
        AppTestModule.withDefaults({
          configurer: (apolloMockController) =>
            mockScrape(apolloMockController),
        }),
        RouterTestingModule.withRoutes([]),
      ],
    }).compileComponents();

    await mockServerSettings(
      TestBed.inject(ApolloMockController),
      TestBed.inject(ServerConfigService),
      TestBed.inject(ApolloClient),
    );

    fixture = TestBed.createComponent(PcTrackerProductPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
