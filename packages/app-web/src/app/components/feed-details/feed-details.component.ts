import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { GqlFeedlessPlugins, GqlProductName, GqlScrapeRequest, GqlVisibility, GqlWebDocumentField } from '../../../generated/graphql';
import { FeedlessPlugin, Repository, SubscriptionSource, WebDocument } from '../../graphql/types';
import { GenerateFeedModalComponentProps, getScrapeRequest } from '../../modals/generate-feed-modal/generate-feed-modal.component';
import { ModalService } from '../../services/modal.service';
import { ModalController, PopoverController } from '@ionic/angular';
import { FeedWithRequest, tagsToString } from '../feed-builder/feed-builder.component';
import { RepositoryService } from '../../services/repository.service';
import { ArrayElement } from '../../types';
import { BubbleColor } from '../bubble/bubble.component';
import { PluginService } from '../../services/plugin.service';
import { Router } from '@angular/router';
import { dateFormat, SessionService } from '../../services/session.service';
import { DocumentService } from '../../services/document.service';
import { ServerSettingsService } from '../../services/server-settings.service';
import { without } from 'lodash-es';
import { Subscription } from 'rxjs';
import { FormControl } from '@angular/forms';
import { relativeTimeOrElse } from '../agents/agents.component';

export type WebDocumentWithFornmControl = WebDocument & { fc: FormControl<boolean> };

type ViewMode = 'list' | 'diff' | 'histogram'

type Pair<A, B> = {
  a: A
  b: B
}

@Component({
  selector: 'app-feed-details',
  templateUrl: './feed-details.component.html',
  styleUrls: ['./feed-details.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeedDetailsComponent implements OnInit, OnDestroy {
  @Input({ required: true })
  repository: Repository;

  @Input()
  track: boolean;

  protected documents: WebDocumentWithFornmControl[] = [];
  protected feedUrl: string;

  private plugins: FeedlessPlugin[];

  protected readonly GqlVisibility = GqlVisibility;
  protected readonly dateFormat = dateFormat;
  showFullDescription: boolean = false;
  protected playDocument: WebDocument;
  private userId: string;
  private subscriptions: Subscription[] = [];
  currentPage: number;
  protected loading: boolean;
  protected isOwner: boolean;
  protected selectAllFc = new FormControl<boolean>(false);
  protected selectedCount: number = 0;
  viewModeFc = new FormControl<ViewMode>('list');
  viewModeList: ViewMode = 'list';
  viewModeHistogram: ViewMode = 'histogram';
  viewModeDiff: ViewMode = 'diff';
  protected compareByField: GqlWebDocumentField | undefined;
  protected readonly GqlProductName = GqlProductName;
  protected readonly compareByPixel: GqlWebDocumentField = GqlWebDocumentField.Pixel;


  constructor(
    private readonly modalService: ModalService,
    private readonly pluginService: PluginService,
    private readonly popoverCtrl: PopoverController,
    private readonly documentService: DocumentService,
    private readonly router: Router,
    protected readonly serverSettings: ServerSettingsService,
    private readonly sessionService: SessionService,
    private readonly repositoryService: RepositoryService,
    private readonly serverSettingsService: ServerSettingsService,
    private readonly changeRef: ChangeDetectorRef,
    private readonly modalCtrl: ModalController,
  ) {}

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  async ngOnInit() {
    if (this.repository.product === GqlProductName.VisualDiff) {
      this.viewModeFc.setValue('diff');
    }
    this.compareByField = this.repository.plugins.find(plugin => plugin.pluginId === GqlFeedlessPlugins.OrgFeedlessDiffEmailForward)?.params?.org_feedless_diff_email_forward?.compareBy?.field;

    this.feedUrl = `${this.serverSettingsService.gatewayUrl}/feed/${this.repository.id}/atom`;
    this.plugins = await this.pluginService.listPlugins();
    this.subscriptions.push(
      this.sessionService.getSession().subscribe((session) => {
        this.userId = session.user?.id;
        this.assessIsOwner();
      }),
      this.selectAllFc.valueChanges.subscribe((isChecked) => {
        this.documents.forEach((document) =>
          document.fc.setValue(isChecked, { emitEvent: false }),
        );
        if (isChecked) {
          this.selectedCount = this.documents.length;
        } else {
          this.selectedCount = 0;
        }
        this.changeRef.detectChanges();
      }),
    );
    await this.fetchPage();
    this.changeRef.detectChanges();
  }

  getPluginsOfRepository(repository: Repository) {
    if (!this.plugins) {
      return '';
    }
    return repository.plugins
      .map((plugin) => this.getPluginName(plugin.pluginId))
      .join(', ');
  }

  hostname(url: string): string {
    try {
      return new URL(url).hostname;
    } catch (e) {
      return 'Unknown';
    }
  }

  async editRepository() {
    const componentProps: GenerateFeedModalComponentProps = {
      repository: this.repository,
      modalTitle: `Customize ${this.repository.title}`,
    };
    await this.modalService.openFeedMetaEditor(componentProps);
    await this.popoverCtrl.dismiss();
  }

  hasErrors(): boolean {
    return (
      this.repository.sources.length === 0 ||
      this.repository.sources.some((s) => s.errornous)
    );
  }

  dismissModal() {
    this.modalCtrl.dismiss();
  }

  getHealthColorForSource(
    source: ArrayElement<Repository['sources']>,
  ): BubbleColor {
    if (source.errornous) {
      return 'red';
    } else {
      return 'green';
    }
  }

  protected async fetchPage(page: number = 0) {
    this.currentPage = page;
    this.selectAllFc.setValue(false);
    this.loading = true;
    this.changeRef.detectChanges();
    const documents = await this.documentService.findAllByStreamId({
      cursor: {
        page,
        pageSize: 10,
      },
      where: {
        repository: {
          where: {
            id: this.repository.id,
          },
        },
      },
    });
    this.documents = documents.map((document) => {
      const fc = new FormControl<boolean>(false);
      this.subscriptions.push(
        fc.valueChanges.subscribe((isChecked) => {
          if (isChecked) {
            this.selectedCount++;
          } else {
            this.selectedCount--;
          }
          this.selectAllFc.setValue(this.selectedCount !== 0, {
            emitEvent: false,
          });
        }),
      );
      return {
        ...document,
        fc: fc,
      };
    });
    this.loading = false;
    this.changeRef.detectChanges();
  }

  getRetentionStrategy(): string {
    if (
      this.repository.retention.maxAgeDays ||
      this.repository.retention.maxItems
    ) {
      if (
        this.repository.retention.maxAgeDays &&
        this.repository.retention.maxItems
      ) {
        return `${this.repository.retention.maxAgeDays} days, ${this.repository.retention.maxItems} items`;
      } else {
        if (this.repository.retention.maxAgeDays) {
          return `${this.repository.retention.maxAgeDays} days`;
        } else {
          return `${this.repository.retention.maxItems} items`;
        }
      }
    } else {
      return 'Auto';
    }
  }

  fromNow = relativeTimeOrElse

  async deleteRepository() {
    await this.repositoryService.deleteRepository({
      id: this.repository.id,
    });
    await this.popoverCtrl.dismiss();
    await this.router.navigateByUrl('/feeds');
  }

  getPluginsOfSource(source: ArrayElement<Repository['sources']>): string {
    if (!this.plugins) {
      return '';
    }
    return source.emit
      .flatMap(
        (emit) =>
          emit.selectorBased?.expose.transformers.flatMap((transformer) =>
            this.getPluginName(transformer.pluginId),
          ),
      )
      .join(', ');
  }

  private getPluginName(pluginId: string) {
    return this.plugins.find((plugin) => plugin.id === pluginId)?.name;
  }

  stringifyTags(source: ArrayElement<Repository['sources']>) {
    return tagsToString(source.tags) || 'Add tags';
  }

  async deleteSource(source: SubscriptionSource) {
    console.log('deleteSource', source);
    this.repository = await this.repositoryService.updateRepository({
      where: {
        id: this.repository.id,
      },
      data: {
        sources: {
          remove: [source.id],
        },
      },
    });
    this.assessIsOwner();
    this.changeRef.detectChanges();
  }

  async editTags(source: ArrayElement<Repository['sources']>) {
    const tags = await this.modalService.openTagModal({
      tags: source.tags || [],
    });
    this.repository = await this.repositoryService.updateRepository({
      where: {
        id: this.repository.id,
      },
      data: {
        sources: {
          update: [
            {
              where: {
                id: source.id,
              },
              data: {
                tags: {
                  set: tags,
                },
              },
            },
          ],
        },
      },
    });
    this.changeRef.detectChanges();
  }

  getTags(document: WebDocument) {
    return tagsToString(document.tags);
  }

  async editSource(source: SubscriptionSource = null) {
    await this.modalService.openFeedBuilder(
      {
        scrapeRequest: source as any,
      },
      async (data: FeedWithRequest) => {
        if (data) {
          this.repository = await this.repositoryService.updateRepository({
            where: {
              id: this.repository.id,
            },
            data: {
              sources: {
                add: [
                  getScrapeRequest(
                    data.feed,
                    data.scrapeRequest as GqlScrapeRequest,
                  ),
                ],
                remove: source ? [source.id] : [],
              },
            },
          });
          this.changeRef.detectChanges();
        }
      },
    );
  }

  playAudio(document: WebDocument): void {
    this.playDocument = document;
  }

  getDocumentUrl(document: WebDocument): string {
    if (this.track) {
      return `${this.serverSettingsService.gatewayUrl}/article/${document.id}`;
    } else {
      return document.url;
    }
  }

  private assessIsOwner() {
    this.isOwner = this.repository?.ownerId === this.userId;
  }

  async deleteAllSelected() {
    const selected = this.documents.filter((document) => document.fc.value);
    await this.documentService.removeById({
      where: {
        repository: {
          where: {
            id: this.repository.id,
          },
        },
        id: {
          in: selected.map((document) => document.id),
        },
      },
    });
    this.documents = without(this.documents, ...selected);
    this.selectAllFc.setValue(false);
    this.changeRef.detectChanges();
  }

  getDocumentPairs() {
    const pairs: Pair<WebDocument, WebDocument>[] = [];
    for(let i=0; i < this.documents.length -1; i++) {
      pairs.push({
        a: this.documents[i+1],
        b: this.documents[i],
      })
    }
    return pairs;
  }
}
