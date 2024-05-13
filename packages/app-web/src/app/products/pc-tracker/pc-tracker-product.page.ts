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
import { ProductConfig, ProductService } from '../../services/product.service';
import { fixUrl } from '../../app.module';
import { ServerSettingsService } from '../../services/server-settings.service';
import { dateFormat } from '../../services/session.service';
import { LicenseService } from '../../services/license.service';
import { GqlLicenseQuery } from '../../../generated/graphql';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { ModalService } from '../../services/modal.service';
import { TrackerEditModalComponentProps } from './tracker-edit/tracker-edit-modal.component';

@Component({
  selector: 'app-page-change-tracker-product-page',
  templateUrl: './pc-tracker-product.page.html',
  styleUrls: ['./pc-tracker-product.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PcTrackerProductPage implements OnInit, OnDestroy {
  scrapeResponse: ScrapeResponse;
  productConfig: ProductConfig;
  url: string;
  private subscriptions: Subscription[] = [];
  license: GqlLicenseQuery['license'];

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly productService: ProductService,
    private readonly licenseService: LicenseService,
    private readonly modalService: ModalService,
    readonly serverSettings: ServerSettingsService,
    private readonly router: Router,
    private readonly changeRef: ChangeDetectorRef,
  ) {
    dayjs.extend(relativeTime);
  }

  async ngOnInit() {
    this.subscriptions.push(
      this.productService
        .getActiveProductConfigChange()
        .subscribe((productConfig) => {
          this.productConfig = productConfig;
          this.changeRef.detectChanges();
        }),
      this.licenseService.licenseChange.subscribe((license) => {
        this.license = license;
        this.changeRef.detectChanges();
      }),
      this.activatedRoute.queryParams.subscribe((queryParams) => {
        if (queryParams.url) {
          this.url = queryParams.url;
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }
  async handleQuery(url: string) {
    try {
      this.url = fixUrl(url);
      await this.router.navigate(['/builder'], {
        queryParams: {
          url: this.url,
        },
      });
    } catch (e) {
      console.warn(e);
    }
  }

  protected readonly dateFormat = dateFormat;

  async openCreateTrackerModal() {
    const props: TrackerEditModalComponentProps = {};
    await this.modalService.openPageTrackerEditor(props);
  }
}