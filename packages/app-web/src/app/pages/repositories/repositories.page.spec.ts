import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { RepositoriesPage } from './repositories.page';
import { RepositoriesPageModule } from './repositories.module';
import { AppTestModule } from '../../app-test.module';
import {
  GqlListSourceSubscriptionsQuery,
  GqlListSourceSubscriptionsQueryVariables,
  ListSourceSubscriptions
} from '../../../generated/graphql';

describe('RepositoriesPage', () => {
  let component: RepositoriesPage;
  let fixture: ComponentFixture<RepositoriesPage>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [
        RepositoriesPageModule,
        AppTestModule.withDefaults((apolloMockController) => {
          apolloMockController
            .mockQuery<
              GqlListSourceSubscriptionsQuery,
              GqlListSourceSubscriptionsQueryVariables
            >(ListSourceSubscriptions)
            .and.resolveOnce(async () => {
              return {
                data: {
                  sourceSubscriptions: [],
                },
              };
            });
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RepositoriesPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
