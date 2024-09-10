import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  OnInit,
  ViewChild,
} from '@angular/core';
import { ModalController, ToastController } from '@ionic/angular';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { RepositoryService } from '../../services/repository.service';
import {
  GqlFeatureName,
  GqlFeedlessPlugins,
  GqlItemFilterParamsInput,
  GqlPluginExecutionInput,
  GqlSourceInput,
  GqlVisibility,
  GqlWebDocumentDateField,
} from '../../../generated/graphql';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { dateFormat, SessionService } from '../../services/session.service';
import { RepositoryFull } from '../../graphql/types';
import { ServerConfigService } from '../../services/server-config.service';
import { isDefined } from '../../types';
import { DEFAULT_FETCH_CRON } from '../../pages/feed-builder/feed-builder.page';

export interface GenerateFeedModalComponentProps {
  repository: RepositoryFull;
  openAccordions?: GenerateFeedAccordion[];
}

// interface TagConditionData {
//   tag: string;
//   field: FilterField;
//   operator: FilterOperator;
//   value: string;
// }

// type ConditionalTagParams = ArrayElement<
//   ArrayElement<Repository['plugins']>['params']['org_feedless_conditional_tag']
// >;

export type GenerateFeedAccordion = 'privacy' | 'storage';

@Component({
  selector: 'app-repository-modal',
  templateUrl: './repository-modal.component.html',
  styleUrls: ['./repository-modal.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RepositoryModalComponent
  implements GenerateFeedModalComponentProps, OnInit
{
  formFg = new FormGroup({
    title: new FormControl<string>('', {
      validators: [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(255),
      ],
    }),
    description: new FormControl<string>('', [Validators.maxLength(500)]),
    maxCapacity: new FormControl<number>(null, [Validators.min(2)]),
    maxAgeDays: new FormControl<number>(null, [Validators.min(1)]),
    ageReferenceField: new FormControl<GqlWebDocumentDateField>(
      GqlWebDocumentDateField.PublishedAt,
    ),
    fetchFrequency: new FormControl<string>(DEFAULT_FETCH_CRON, {
      nonNullable: true,
      validators: Validators.pattern('([^ ]+ ){5}[^ ]+'),
    }),
    applyFulltextPlugin: new FormControl<boolean>(false),
    transformToReadability: new FormControl<boolean>(true),
    isPublic: new FormControl<boolean>(false),
    applyPrivacyPlugin: new FormControl<boolean>(false),
    applyConditionalTagsPlugin: new FormControl<boolean>(false),
  });
  // conditionalTags: FormGroup<TypedFormGroup<TagConditionData>>[] = [];

  @Input({ required: true })
  repository: RepositoryFull;

  loading = false;
  errorMessage: string;
  isLoggedIn: boolean;

  protected readonly dateFormat = dateFormat;
  protected readonly GqlWebDocumentDateField = GqlWebDocumentDateField;
  @Input()
  openAccordions: GenerateFeedAccordion[] = [];
  accordionPrivacy: GenerateFeedAccordion = 'privacy';
  accordionStorage: GenerateFeedAccordion = 'storage';

  private filterParams: GqlItemFilterParamsInput[];

  constructor(
    private readonly modalCtrl: ModalController,
    private readonly toastCtrl: ToastController,
    private readonly sessionService: SessionService,
    readonly serverConfig: ServerConfigService,
    private readonly router: Router,
    private readonly changeRef: ChangeDetectorRef,
    private readonly repositoryService: RepositoryService,
  ) {}

  closeModal() {
    return this.modalCtrl.dismiss();
  }

  // addConditionalTag(data: ConditionalTagParams = null) {
  //   if (this.conditionalTags.some((filter) => filter.invalid)) {
  //     return;
  //   }
  //
  //   const filter = new FormGroup({
  //     tag: new FormControl<string>('', [Validators.required]),
  //     field: new FormControl<FilterField>('title', [Validators.required]),
  //     operator: new FormControl<FilterOperator>(
  //       GqlStringFilterOperator.Contains,
  //       [Validators.required],
  //     ),
  //     value: new FormControl<string>('', [
  //       Validators.required,
  //       Validators.minLength(1),
  //     ]),
  //   });
  //
  //   // if (data) {
  //   //   const type = Object.keys(data).find(
  //   //     (field) => field != '__typename' && !!data[field],
  //   //   );
  //   //   const field = Object.keys(data[type]).find(
  //   //     (field) => field != '__typename' && !!data[type][field],
  //   //   );
  //   //   filter.patchValue({
  //   //     tag: type as any,
  //   //     field: field as any,
  //   //     value: data[type][field].value,
  //   //     operator: data[type][field].operator,
  //   //   });
  //   // }
  //
  //   this.conditionalTags.push(filter);
  //   filter.statusChanges.subscribe((status) => {
  //     if (status === 'VALID') {
  //       this.filterChanges.next();
  //     }
  //   });
  // }

  // removeConditionalTag(index: number) {
  //   this.conditionalTags = without(
  //     this.conditionalTags,
  //     this.conditionalTags[index],
  //   );
  //   this.filterChanges.next();
  // }

  async createOrUpdateFeed() {
    if (this.formFg.invalid) {
      return;
    }

    this.loading = true;
    this.changeRef.detectChanges();

    try {
      const plugins: GqlPluginExecutionInput[] = [];

      const hasFilters = this.filterParams.length > 0;
      const applyFiltersLast = false;

      const appendFilterPlugin = () => {
        plugins.push({
          pluginId: GqlFeedlessPlugins.OrgFeedlessFilter,
          params: {
            org_feedless_filter: this.filterParams,
            // org_feedless_conditional_tag: this.getConditionalTagsParams(),
          },
        });
      };

      if (!applyFiltersLast && hasFilters) {
        appendFilterPlugin();
      }
      if (this.formFg.value.applyFulltextPlugin) {
        plugins.push({
          pluginId: GqlFeedlessPlugins.OrgFeedlessFulltext,
          params: {
            org_feedless_fulltext: {
              readability: this.formFg.value.transformToReadability,
              inheritParams: true,
            },
          },
        });
      }
      if (this.formFg.value.applyPrivacyPlugin) {
        plugins.push({
          pluginId: GqlFeedlessPlugins.OrgFeedlessPrivacy,
          params: {},
        });
      }
      if (applyFiltersLast && hasFilters) {
        appendFilterPlugin();
      }
      if (this.isUpdate()) {
        const {
          title,
          isPublic,
          description,
          fetchFrequency,
          maxAgeDays,
          maxCapacity,
          ageReferenceField,
        } = this.formFg.value;
        await this.repositoryService.updateRepository({
          where: {
            id: this.repository.id,
          },
          data: {
            plugins,
            visibility: {
              set: isPublic ? GqlVisibility.IsPublic : GqlVisibility.IsPrivate,
            },
            title: {
              set: title,
            },
            description: {
              set: description,
            },
            refreshCron: {
              set: fetchFrequency,
            },
            retention: {
              maxCapacity: {
                set: maxCapacity || null,
              },
              maxAgeDays: {
                set: maxAgeDays || null,
              },
              ageReferenceField: {
                set: ageReferenceField,
              },
            },
          },
        });
        const toast = await this.toastCtrl.create({
          message: 'Saved',
          duration: 3000,
          color: 'success',
        });

        await toast.present();
      } else {
        const repositories = await this.repositoryService.createRepositories({
          repositories: [
            {
              product: environment.product,
              sources: this.repository.sources as GqlSourceInput[],
              sinkOptions: {
                title: this.formFg.value.title,
                refreshCron: this.formFg.value.fetchFrequency,
                withShareKey: true,
                description: this.formFg.value.description,
                visibility: this.formFg.value.isPublic
                  ? GqlVisibility.IsPublic
                  : GqlVisibility.IsPrivate,
                plugins,
              },
            },
          ],
        });

        const firstRepository = repositories[0];
        await this.modalCtrl.dismiss();
        await this.router.navigateByUrl(`/feeds/${firstRepository.id}`);
      }
    } catch (e) {
      this.errorMessage = e.message;
    } finally {
      this.loading = false;
    }
    this.changeRef.detectChanges();
  }

  async ngOnInit(): Promise<void> {
    this.isLoggedIn = this.sessionService.isAuthenticated();

    const canPublicRepository = this.serverConfig.getFeatureValueBool(
      GqlFeatureName.PublicRepository,
    );
    if (!canPublicRepository) {
      this.formFg.controls.isPublic.disable();
      this.formFg.controls.isPublic.setErrors({
        disabled: 'Feature disabled for you',
      });
    }

    const maxItemsLowerLimit = this.serverConfig.getFeatureValueInt(
      GqlFeatureName.RepositoryCapacityLowerLimitInt,
    );
    if (maxItemsLowerLimit) {
      this.formFg.controls.maxCapacity.addValidators([
        Validators.min(maxItemsLowerLimit),
      ]);
    }
    const maxItemsUpperLimit = this.serverConfig.getFeatureValueInt(
      GqlFeatureName.RepositoryCapacityUpperLimitInt,
    );
    if (maxItemsUpperLimit) {
      this.formFg.controls.maxCapacity.addValidators([
        Validators.max(maxItemsUpperLimit),
      ]);
    }

    const maxDaysLowerLimit = this.serverConfig.getFeatureValueInt(
      GqlFeatureName.RepositoryRetentionMaxDaysLowerLimitInt,
    );
    if (maxDaysLowerLimit) {
      this.formFg.controls.maxAgeDays.addValidators([
        Validators.min(maxDaysLowerLimit),
      ]);
    }

    const retention = this.repository.retention;
    this.formFg.patchValue({
      title: this.repository.title.substring(0, 255),
      isPublic: this.repository.visibility == GqlVisibility.IsPublic,
      description: this.repository.description,
      fetchFrequency: this.repository.refreshCron || DEFAULT_FETCH_CRON,
      applyFulltextPlugin: this.repository.plugins.some(
        (p) => p.pluginId === GqlFeedlessPlugins.OrgFeedlessFulltext,
      ),
      applyPrivacyPlugin: this.repository.plugins.some(
        (p) => p.pluginId === GqlFeedlessPlugins.OrgFeedlessPrivacy,
      ),
      maxAgeDays: retention?.maxAgeDays || null,
      maxCapacity: retention?.maxCapacity || null,
    });
    this.formFg.markAllAsTouched();
  }

  // private getConditionalTagsParams(): GqlConditionalTagInput[] {
  //   return this.conditionalTags
  //     .filter((filterFg) => filterFg.valid)
  //     .map((filterFg) => filterFg.value)
  //     .map<GqlConditionalTagInput>((data) => ({
  //       tag: data.tag,
  //       filter: {
  //         [data.field]: {
  //           value: data.value,
  //           operator: data.operator,
  //         },
  //       },
  //     }));
  // }

  isUpdate() {
    return isDefined(this.repository.id);
  }

  getFilterPlugin() {
    return this.repository.plugins.find(
      (p) => p.pluginId === GqlFeedlessPlugins.OrgFeedlessFilter,
    )?.params?.org_feedless_filter;
  }

  handleFilterChange(filterParams: GqlItemFilterParamsInput[]) {
    this.filterParams = filterParams;
    // this.loadFeedPreview()
  }
}