import { inject, Injectable } from '@angular/core';
import { AppProduct } from '../app.module';
import { Router, Routes } from '@angular/router';
import { AuthGuardService } from '../guards/auth-guard.service';
import { FallbackRedirectService } from '../guards/fallback-redirect.service';
import { environment } from '../../environments/environment';
import { Title } from '@angular/platform-browser';

export interface SideMenuConfig {
  width: number;
}

export interface ProductConfig {
  product: AppProduct;
  name: string;
  pageTitle: string;
  sideMenu?: SideMenuConfig;
  routes: Routes;
}

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  private routes: Routes = [
    {
      path: 'login',
      loadChildren: () =>
        import('../pages/login/login.module').then((m) => m.LoginPageModule),
    },
    {
      path: 'contact',
      loadChildren: () =>
        import('../pages/contact/contact.module').then(
          (m) => m.ContactPageModule,
        ),
    },
    {
      path: 'plans',
      loadChildren: () =>
        import('../pages/plans/plans.module').then((m) => m.PlansPageModule),
    },
    {
      path: 'agents',
      loadChildren: () =>
        import('../pages/agents/agents.module').then((m) => m.AgentsPageModule),
    },
    {
      path: 'profile',
      canActivate: [AuthGuardService],
      loadChildren: () =>
        import('../pages/profile/profile.module').then(
          (m) => m.ProfilePageModule,
        ),
    },
    {
      path: 'terms',
      loadChildren: () =>
        import('../pages/terms/terms.module').then((m) => m.TermsPageModule),
    },
    {
      path: 'privacy',
      loadChildren: () =>
        import('../pages/privacy/privacy.module').then(
          (m) => m.PrivacyPageModule,
        ),
    },
    {
      path: 'reader',
      loadChildren: () =>
        import('../pages/reader/reader.module').then((m) => m.ReaderPageModule),
    },
    {
      path: 'getting-started',
      loadChildren: () =>
        import('../pages/getting-started/getting-started.module').then(
          (m) => m.GettingStartedPageModule,
        ),
    },
    {
      path: 'sources',
      loadChildren: () =>
        import('../pages/sources/sources.module').then(
          (m) => m.SourcesPageModule,
        ),
    },
    {
      path: 'notifications',
      canActivate: [AuthGuardService],
      loadChildren: () =>
        import('../pages/notifications/notifications.module').then(
          (m) => m.NotificationsPageModule,
        ),
    },
    {
      path: 'cli',
      canActivate: [AuthGuardService],
      loadChildren: () =>
        import('../pages/link-cli/link-cli.module').then(
          (m) => m.LinkCliPageModule,
        ),
    },
    {
      path: '',
      canActivate: [() => inject(FallbackRedirectService).canActivate()],
      children: [],
    },
  ];

  private products: ProductConfig[] = [
    {
      product: 'reader',
      name: 'Reader',
      pageTitle: 'Reader',
      sideMenu: {
        width: 300,
      },
      routes: [
        {
          path: '',
          loadChildren: () =>
            import('../pages/reader/reader.module').then(
              (m) => m.ReaderPageModule,
            ),
        },
      ],
    },
    {
      product: 'rss-builder',
      name: 'RSS Builder',
      pageTitle: 'RSS Builder',
      routes: [
        {
          path: '',
          loadChildren: () =>
            import('../products/rss-builder/rss-builder.module').then(
              (m) => m.RssBuilderPageModule,
            ),
        },
      ],
    },
    {
      product: 'scrape-api',
      name: 'Scrape API',
      pageTitle: 'Scrape API',
      routes: [
        {
          path: '',
          loadChildren: () =>
            import('../products/scrape-api/scrape-api.module').then(
              (m) => m.ScrapeApiPageModule,
            ),
        },
      ],
    },
    {
      product: 'feedless',
      name: 'feedless',
      pageTitle: 'feedless',
      sideMenu: {
        width: 200,
      },
      routes: this.routes,
    },
    {
      product: 'visual-diff',
      name: 'VisualDiff',
      pageTitle: 'VisualDiff',
      routes: [
        {
          path: '',
          // canMatch: [() => true],
          loadChildren: () =>
            import('../products/visual-diff/visual-diff.module').then(
              (m) => m.VisualDiffPageModule,
            ),
        },
      ],
    },
  ];

  constructor(private readonly router: Router,
              private readonly titleService: Title) {}

  resolveProduct(product: AppProduct) {
    environment.product = () => product;
    console.log(`resolveProduct ${environment.product()}`);
    const config = this.getProductConfig();
    this.titleService.setTitle(config.pageTitle);
    this.router.resetConfig(config.routes);
  }

  getProductConfig(): ProductConfig {
    return this.products.find(
      (product) => product.product === environment.product(),
    );
  }
}
