import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ScrapeResponse } from '../../graphql/types';
import { SessionService } from '../../services/session.service';
import {
  AppConfigService,
  ProductConfig,
} from '../../services/app-config.service';
import { Authentication, AuthService } from '../../services/auth.service';
import { GqlProductCategory } from '../../../generated/graphql';

@Component({
  selector: 'app-feedless-product-page',
  templateUrl: './feedless-product.page.html',
  styleUrls: ['./feedless-product.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeedlessProductPage implements OnInit, OnDestroy {
  scrapeResponse: ScrapeResponse;
  productConfig: ProductConfig;
  url: string;
  private subscriptions: Subscription[] = [];
  authorization: Authentication;

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly appConfigService: AppConfigService,
    private readonly authService: AuthService,
    private readonly changeRef: ChangeDetectorRef,
    private readonly router: Router,
    readonly profile: SessionService,
  ) {}

  async ngOnInit() {
    this.subscriptions.push(
      this.authService.authorizationChange().subscribe((authorization) => {
        this.authorization = authorization;
      }),
      this.appConfigService
        .getActiveProductConfigChange()
        .subscribe((productConfig) => {
          this.productConfig = productConfig;
          this.changeRef.detectChanges();
        }),
      this.activatedRoute.queryParams.subscribe((queryParams) => {
        if (queryParams.url) {
          this.url = queryParams.url;
          this.changeRef.detectChanges();
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  // async handleQuery(url: string) {
  //   try {
  //     this.url = fixUrl(url);
  //     await this.router.navigate(['/feed-builder'], {
  //       queryParams: {
  //         url: this.url,
  //       },
  //     });
  //   } catch (e) {
  //     console.warn(e);
  //   }
  // }
  protected readonly GqlProductName = GqlProductCategory;
}
