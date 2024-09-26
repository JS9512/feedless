import {
  ChangeDetectorRef,
  Component,
  Input,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { relativeTimeOrElse } from '../agents/agents.component';
import { GqlProductCategory } from '../../../generated/graphql';
import {
  AppConfigService,
  ProductConfig,
} from '../../services/app-config.service';
import { Subscription } from 'rxjs';
import { Authentication, AuthService } from '../../services/auth.service';
import { Session } from '../../graphql/types';
import { ActivatedRoute } from '@angular/router';
import { ServerConfigService } from '../../services/server-config.service';
import { SessionService } from '../../services/session.service';

@Component({
  selector: 'app-feedless-header',
  templateUrl: './feedless-header.component.html',
  styleUrls: ['./feedless-header.component.scss'],
})
export class FeedlessHeaderComponent implements OnInit, OnDestroy {
  protected productConfig: ProductConfig;
  private subscriptions: Subscription[] = [];
  protected authorization: Authentication;
  protected session: Session;
  protected readonly GqlProductName = GqlProductCategory;
  protected fromNow = relativeTimeOrElse;

  constructor(
    private readonly appConfigService: AppConfigService,
    private readonly authService: AuthService,
    readonly serverConfig: ServerConfigService,
    private readonly sessionService: SessionService,
    private readonly changeRef: ChangeDetectorRef,
    readonly profile: SessionService,
  ) {}

  async ngOnInit() {
    this.subscriptions.push(
      this.sessionService.getSession().subscribe((session) => {
        this.session = session;
      }),
      this.authService.authorizationChange().subscribe((authorization) => {
        this.authorization = authorization;
      }),
      this.appConfigService
        .getActiveProductConfigChange()
        .subscribe((productConfig) => {
          this.productConfig = productConfig;
          this.changeRef.detectChanges();
        }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  async cancelAccountDeletion() {
    await this.sessionService.updateCurrentUser({
      purgeScheduledFor: {
        assignNull: true,
      },
    });
    location.reload();
  }
}