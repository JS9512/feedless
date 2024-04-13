import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { ProductsPage } from './products.page';
import { ProductsPageModule } from './products.module';
import { AppTestModule, mockPlans } from '../../../app-test.module';
import { RouterTestingModule } from '@angular/router/testing';
import { ProductService } from '../../../services/product.service';

describe('ProductsPage', () => {
  let component: ProductsPage;
  let fixture: ComponentFixture<ProductsPage>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [
        ProductsPageModule,
        AppTestModule.withDefaults((apolloMockController) => {
          mockPlans(apolloMockController);
        }),
        RouterTestingModule.withRoutes([]),
      ],
    }).compileComponents();

    const productService = TestBed.inject(ProductService);
    productService.getProductConfigs = () => Promise.resolve([]);

    fixture = TestBed.createComponent(ProductsPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
