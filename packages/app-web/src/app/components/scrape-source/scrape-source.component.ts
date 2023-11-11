import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild
} from '@angular/core';
import { ProfileService } from '../../services/profile.service';
import { debounce, interval, Subscription } from 'rxjs';
import { Embeddable } from '../embedded-website/embedded-website.component';
import {
  GqlCookieValueInput,
  GqlDomActionSelectInput,
  GqlDomActionTypeInput,
  GqlDomElementByNameInput,
  GqlDomElementByNameOrXPathInput,
  GqlDomElementByXPathInput,
  GqlDomElementInput,
  GqlPuppeteerWaitUntil,
  GqlRequestHeaderInput,
  GqlScrapeAction,
  GqlScrapeActionInput,
  GqlScrapeEmitInput,
  GqlScrapeEmitType,
  GqlScrapePrerenderInput,
  GqlScrapeRequestInput,
  GqlWaitActionInput,
  GqlXyPosition,
  GqlXyPositionInput,
  InputMaybe
} from '../../../generated/graphql';
import { ScrapeService } from '../../services/scrape.service';
import { FormArray, FormControl, FormControlOptions, FormGroup, Validators, ɵValue } from '@angular/forms';
import { fixUrl, isValidUrl } from '../../pages/getting-started/getting-started.page';
import { ScrapeResponse } from '../../graphql/types';
import { KeyLabelOption } from '../select/select.component';
import { BoundingBox, XyPosition } from '../embedded-image/embedded-image.component';
import { isDefined, ResponseMapper } from '../../modals/feed-builder-modal/scrape-builder';
import { without } from 'lodash-es';
import {
  NativeOrGenericFeed,
  TransformWebsiteToFeedComponent,
  TransformWebsiteToFeedComponentProps
} from '../transform-website-to-feed/transform-website-to-feed.component';
import { ModalController } from '@ionic/angular';

type View = 'screenshot' | 'markup';


type ActionType = keyof GqlScrapeAction

type AnyPrimitive = string | number | boolean
export type TypedFormGroup<TYPE> = {
  [K in keyof TYPE]: TYPE[K] extends AnyPrimitive
    ? FormControl<TYPE[K] | null>
    : (
      TYPE[K] extends Array<infer U>
        ? (
          U extends AnyPrimitive ? FormArray<FormControl<U | null>>
            : FormArray<FormGroup<TypedFormGroup<U>>>
          )
        : FormGroup<TypedFormGroup<TYPE[K]>>
      );
};

type ClickType = 'element' | 'position'

type FragmentType = 'boundingBox' | 'element'

interface ScrapeEmits {
  emit: GqlScrapeEmitInput[],
  debug?: {
    screenshot?: boolean,
    console?: boolean,
    cookies?: boolean,
    html?: boolean
  }
}

interface ScreenResolution {
  name: string;
  width: number;
  height: number;
}

type RenderEngine = 'static' | 'chrome';

type SourceForm = {
  url: FormControl<string>,
  renderEngine: FormControl<RenderEngine>,
  screenResolution: FormControl<ScreenResolution>,
  actions: FormArray<FormGroup<TypedFormGroup<ScrapeAction>>>
};

type OneOfClick = {
  type: ClickType;
  oneOf: GqlDomElementInput;
};

type OneOfAction = {
  cookie?: InputMaybe<GqlCookieValueInput>;
  header?: InputMaybe<GqlRequestHeaderInput>;
  select?: InputMaybe<GqlDomActionSelectInput>;
  type?: InputMaybe<GqlDomActionTypeInput>;
  wait?: InputMaybe<GqlWaitActionInput>;
  click?: OneOfClick
};

type ScrapeAction = {
  type: ActionType;
  oneOf?: OneOfAction
};

export interface ScrapeSourceComponentProps {
  scrapeRequest: GqlScrapeRequestInput;
}

@Component({
  selector: 'app-scrape-source',
  templateUrl: './scrape-source.component.html',
  styleUrls: ['./scrape-source.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScrapeSourceComponent implements OnInit, OnDestroy, ScrapeSourceComponentProps {

  @Output()
  requestChanged: EventEmitter<GqlScrapeRequestInput> = new EventEmitter<GqlScrapeRequestInput>();

  @Output()
  responseChanged: EventEmitter<ScrapeResponse> = new EventEmitter<ScrapeResponse>();

  @Input()
  scrapeRequest: GqlScrapeRequestInput;

  private scrapeResponse: ScrapeResponse;

  formGroup: FormGroup<SourceForm> = new FormGroup<SourceForm>({
    url: new FormControl<RenderEngine>(null, { nonNullable: true, validators: [Validators.required, Validators.minLength(4)] }),
    renderEngine: new FormControl<RenderEngine>('static', { nonNullable: true, validators: [Validators.required] }),
    screenResolution: new FormControl<ScreenResolution>(null, { nonNullable: true, validators: [Validators.required] }),
    actions: new FormArray<FormGroup<TypedFormGroup<ScrapeAction>>>([])
  }, { updateOn: 'change' });

  private subscriptions: Subscription[] = [];

  isDarkMode: boolean;
  embedMarkup: Embeddable;
  embedScreenshot: Embeddable;

  screenResolutions: KeyLabelOption<ScreenResolution>[] = [
    {
      name: 'XGA',
      width: 1024,
      height: 768
    },
    {
      name: 'HD720',
      width: 1280,
      height: 720
    },
    {
      name: 'WXGA',
      width: 1280,
      height: 800
    },
    {
      name: 'SXGA',
      width: 1280,
      height: 1024
    }
  ].map(sr => ({
      key: sr,
      label: `${sr.name} ${sr.width}x${sr.height}`
    }
  ));


  loading = false;
  view: View = 'screenshot';
  pickElementDelegate: (xpath: string | null) => void;
  pickPositionDelegate: (position: GqlXyPosition | null) => void;
  pickBoundingBoxDelegate: (boundingBox: BoundingBox | null) => void;

  protected readonly isDefined = isDefined;

  totalTime: string;
  renderOptions: KeyLabelOption<RenderEngine>[] = [
    { key: 'static', label: 'Static Response' },
    { key: 'chrome', label: 'Headless Browser' }
  ];
  errorMessage: string;
  highlightXpath: string;

  mapperFg = new FormGroup({
    type: new FormControl<ResponseMapper>(null, { nonNullable: true, validators: [Validators.required] }),
    oneOf: new FormGroup({
      feed: new FormControl<NativeOrGenericFeed>(null, { nonNullable: true, validators: [Validators.required] }),
      fragment: new FormGroup({
        fragmentType: new FormControl<FragmentType>('element', { nonNullable: true, validators: [Validators.required] }),
        boundingBox: new FormGroup({
          x: new FormControl<number>(0, { nonNullable: false, validators: [Validators.required] }),
          y: new FormControl<number>(0, { nonNullable: false, validators: [Validators.required] }),
          h: new FormControl<number>(0, { nonNullable: false, validators: [Validators.required, Validators.min(10)] }),
          w: new FormControl<number>(0, { nonNullable: false, validators: [Validators.required, Validators.min(10)] })
        }),
        xpath: new FormControl<string>('', { nonNullable: false, validators: [Validators.required, Validators.minLength(2)] })
      })
    })
  });

  clickTypes: KeyLabelOption<ClickType>[] = [
    {
      key: 'element',
      label: 'Element',
    },
    {
      key: 'position',
      label: 'Position'
    }
  ];
  fragmentTypes: KeyLabelOption<FragmentType>[] = [
    {
      key: 'element',
      label: 'Element',
    },
    {
      key: 'boundingBox',
      label: 'Bounding Box'
    }
  ];

  scrapeActionOptions: KeyLabelOption<ActionType>[] = [
    {
      key: 'click',
      label: 'Click',
      default: true
    },
    {
      key: 'cookie',
      label: 'Cookie'
    },
    {
      key: 'header',
      label: 'Header'
    },
    {
      key: 'select',
      label: 'Select'
    },
    {
      key: 'type',
      label: 'Type'
    },
    {
      key: 'wait',
      label: 'Wait'
    }
  ];

  constructor(
    readonly profile: ProfileService,
    private readonly changeRef: ChangeDetectorRef,
    private readonly modalCtrl: ModalController,
    private readonly scrapeService: ScrapeService
  ) {
    this.mapperFg.controls.type.valueChanges.subscribe(type => {
      Object.keys(this.mapperFg.controls.oneOf.controls)
        .forEach(otherKey => {
          if (otherKey === type) {
            this.mapperFg.controls.oneOf.controls[otherKey].enable();
          } else {
            this.mapperFg.controls.oneOf.controls[otherKey].disable();
          }
        });
    });
  }

  async ngOnInit() {
    if (this.scrapeRequest) {
      this.formGroup.controls.url.setValue(this.scrapeRequest.page.url);
      this.formGroup.controls.renderEngine.setValue(this.scrapeRequest.page.prerender ? 'chrome' : 'static');
      this.formGroup.controls.screenResolution.setValue(this.screenResolutions[0].key);

      if (this.scrapeRequest.page.actions) {
        this.scrapeRequest.page.actions.forEach(action => this.addAction(action));
      }
      await this.scrapeUrl();
      // this.changeRef.detectChanges();
    }
    if (this.scrapeResponse) {
      this.handleScrapeResponse(this.scrapeResponse);
      this.changeRef.detectChanges();
    }
    this.subscriptions.push(
      this.profile.watchColorScheme().subscribe((isDarkMode) => {
        this.isDarkMode = isDarkMode;
        this.changeRef.detectChanges();
      }),
      // this.formGroup.valueChanges
      //   .pipe(debounce(() => interval(200)))
      //   .subscribe(values => {
      //     console.log('formChange', values);
      //     // return this.scrapeUrl();
      //   })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  async scrapeUrl() {
    try {
      if (!isValidUrl(this.formGroup.value.url)) {
        this.formGroup.controls.url.setValue(fixUrl(this.formGroup.value.url));
      }
      this.changeRef.detectChanges();

      if (this.loading) {
        return;
      }

      this.errorMessage = null;
      this.loading = true;
      this.changeRef.detectChanges();

      const scrapeRequest = this.getScrapeRequest();
      this.requestChanged.emit(scrapeRequest);

      const scrapeResponse = await this.scrapeService.scrape(scrapeRequest);
      this.handleScrapeResponse(scrapeResponse);

      this.formGroup.markAsUntouched();

    } catch (e) {
      this.errorMessage = e.message;
    }
    this.loading = false;
    this.changeRef.detectChanges();
  }

  handlePickedXpath(xpath: string) {
    if (this.pickElementDelegate) {
      this.highlightXpath = xpath;
      this.pickElementDelegate(xpath);
      this.pickElementDelegate = null;
    }
  }

  handlePickedPosition(position: XyPosition | null) {
    if (this.pickPositionDelegate) {
      this.pickPositionDelegate(position);
      this.pickPositionDelegate = null;
      this.changeRef.detectChanges();
    }
  }

  handlePickedBoundingBox(boundingBox: BoundingBox | null) {
    if (this.pickBoundingBoxDelegate) {
      this.pickBoundingBoxDelegate(boundingBox);
      this.pickBoundingBoxDelegate = null;
      this.changeRef.detectChanges();
    }
  }

  private getScrapeRequest(debug = true): GqlScrapeRequestInput {

    let prerender: InputMaybe<GqlScrapePrerenderInput>;

    if (this.formGroup.value.renderEngine === 'chrome') {
      prerender = {
        waitUntil: GqlPuppeteerWaitUntil.Load,
        viewport: {
          isMobile: false,
          height: this.formGroup.value.screenResolution.height,
          width: this.formGroup.value.screenResolution.width
        }
      };
    }

    const emit = this.getScrapeEmits(debug);
    return {
      page: {
        url: this.formGroup.value.url,
        actions: this.toScrapeActions(),
        prerender
      },
      ...emit
    };
  }

  private handleScrapeResponse(scrapeResponse: ScrapeResponse) {
    this.scrapeResponse = scrapeResponse;
    if (scrapeResponse.failed) {
      this.errorMessage = scrapeResponse.errorMessage;
    } else {
      this.responseChanged.emit(scrapeResponse);
      this.totalTime =
        (
          (scrapeResponse.debug.metrics.queue + scrapeResponse.debug.metrics.render) /
          1000
        ).toFixed(2) + 's';
      this.view = 'markup';
      console.log('response.debug.contentType', scrapeResponse.debug.contentType);
      const url = this.formGroup.value.url;
      this.embedMarkup = {
        mimeType: scrapeResponse.debug.contentType,
        data: scrapeResponse.debug.html,
        url,
        viewport: scrapeResponse.debug.viewport
      };
      if (scrapeResponse.debug.screenshot) {
        this.view = 'screenshot';
        this.embedScreenshot = {
          mimeType: 'image/png',
          data: scrapeResponse.debug.screenshot,
          url,
          viewport: scrapeResponse.debug.viewport
        };
      }
    }
  }

  private async ensureScreenshotExists() {
    if (!this.embedScreenshot && this.formGroup.value.renderEngine !== 'chrome') {
      this.formGroup.controls.renderEngine.setValue('chrome');
    }
  }

  isPickAnyModeActive(): boolean {
    return isDefined(this.pickPositionDelegate) || isDefined(this.pickElementDelegate) || isDefined(this.pickBoundingBoxDelegate);
  }

  getPickModeLabel(): string {
    if (isDefined(this.pickPositionDelegate)) {
      return 'Pick a position';
    }
    if (isDefined(this.pickElementDelegate)) {
      return 'Pick an element';
    }
    if (isDefined(this.pickBoundingBoxDelegate)) {
      return 'Draw a bounding box';
    }
    throw new Error('not supported');
  }

  resetPickMode() {
    this.pickPositionDelegate = null;
    this.pickElementDelegate = null;
    this.pickBoundingBoxDelegate = null;
    this.changeRef.detectChanges();
  }

  deleteAction(action: FormGroup<TypedFormGroup<ScrapeAction>>) {
    this.formGroup.controls.actions.removeAt(this.formGroup.controls.actions.controls.indexOf(action))
  }

  async addAction(action?: GqlScrapeActionInput) {
    await this.ensureScreenshotExists();
    const type =
      Object.keys(action || {}).find((attr) => isDefined(action[attr])) ||
      ('click' as any);

    const strFcOptions = (): FormControlOptions => ({ nonNullable: true, validators: [Validators.required, Validators.minLength(1)] });

    const elementByNameOrXpath = (name: string) => new FormGroup<TypedFormGroup<GqlDomElementByNameOrXPathInput>>({
      name: new FormGroup<TypedFormGroup<GqlDomElementByNameInput>>({
        value: new FormControl<string>(name, strFcOptions())
      }),
      xpath: new FormGroup<TypedFormGroup<GqlDomElementByXPathInput>>({
        value: new FormControl<string>(null, strFcOptions())
      })
    });
    const actionFc: FormGroup<TypedFormGroup<ScrapeAction>> = new FormGroup<TypedFormGroup<ScrapeAction>>({
      type: new FormControl<ActionType>(null),
      oneOf: new FormGroup<TypedFormGroup<OneOfAction>>({
        header: new FormGroup<TypedFormGroup<GqlRequestHeaderInput>>({
          name: new FormControl<string>(null, strFcOptions()),
          value: new FormControl<string>(null, strFcOptions())
        }),
        wait: new FormGroup<TypedFormGroup<GqlWaitActionInput>>({
          element: elementByNameOrXpath(action?.wait?.element?.name?.value)
        }),
        cookie: new FormGroup<TypedFormGroup<GqlCookieValueInput>>({
          value: new FormControl<string>(null, strFcOptions())
        }),
        select: new FormGroup<TypedFormGroup<GqlDomActionSelectInput>>({
          selectValue: new FormControl<string>(null, strFcOptions()),
          element: new FormGroup<TypedFormGroup<GqlDomElementByXPathInput>>({
            value: new FormControl<string>(null, strFcOptions())
          })
        }),
        type: new FormGroup<TypedFormGroup<GqlDomActionTypeInput>>({
          typeValue: new FormControl<string>(null, strFcOptions()),
          element: new FormGroup<TypedFormGroup<GqlDomElementByXPathInput>>({
            value: new FormControl<string>(null, strFcOptions())
          })
        }),
        click: new FormGroup<TypedFormGroup<OneOfClick>>({
          type: new FormControl<ClickType>(null, { nonNullable: true }),
          oneOf: new FormGroup<TypedFormGroup<GqlDomElementInput>>({
            element: elementByNameOrXpath(action?.click?.element?.name?.value),
            position: new FormGroup<TypedFormGroup<GqlXyPositionInput>>({
              x: new FormControl<number>(action?.click?.position?.x, {
                nonNullable: true,
                validators: [Validators.required, Validators.min(1)]
              }),
              y: new FormControl<number>(action?.click?.position?.y, {
                nonNullable: true,
                validators: [Validators.required, Validators.min(1)]
              })
            })
          })
        })
      })
    });

    actionFc.controls.type.valueChanges.subscribe(type => {
      Object.keys(actionFc.controls.oneOf.controls)
        .forEach(otherKey => {
          if (otherKey === type) {
            actionFc.controls.oneOf.controls[otherKey].enable();
          } else {
            actionFc.controls.oneOf.controls[otherKey].disable();
          }
        });
    });
    actionFc.controls.oneOf.controls.click.controls.type.valueChanges.subscribe(type => {
      Object.keys(actionFc.controls.oneOf.controls.click.controls.oneOf.controls)
        .forEach(otherKey => {
          if (otherKey === type) {
            actionFc.controls.oneOf.controls.click.controls.oneOf.controls[otherKey].enable();
          } else {
            actionFc.controls.oneOf.controls.click.controls.oneOf.controls[otherKey].disable();
          }
        });
    });

    actionFc.patchValue({
      type,
      oneOf: {
        header: {
          name: action?.header?.name,
          value: action?.header?.name
        },
        type: {
          element: {
            value: action?.type?.element?.value
          },
          typeValue: action?.type?.typeValue
        },
        cookie: {
          value: action?.cookie?.value
        },
        select: {
          selectValue: action?.select?.selectValue,
          element: {
            value: action?.select?.element?.value
          }
        },
        click: {
          type: 'element',
          oneOf: {
            position: {
              x: action?.click?.position?.x,
              y: action?.click?.position?.y
            },
            element: {
              name: action?.click?.element?.name,
              xpath: action?.click?.element?.xpath
            }
          }
        }
      }
    });

    this.formGroup.controls.actions.push(actionFc);

    // actionFc.statusChanges
    //   .pipe(debounce(() => interval(200)))
    //   .subscribe(values => {
    //     console.log('dirty');
    //     // return this.scrapeUrl();
    //   });

    // this.emitActions();
    this.changeRef.detectChanges();
  }

  private toScrapeActions(): GqlScrapeActionInput[] {
    return this.formGroup.controls.actions.controls.map<GqlScrapeActionInput>(
      (actionFromGroup) => {
        const control = actionFromGroup.value;
        const type = control.type;
        switch (type) {
          case 'click':
            const clickType = control.oneOf.click.type;
            if (clickType == 'element') {
              return {
                click: {
                  element: {
                    xpath: {
                      value: control.oneOf.click.oneOf.element.xpath.value
                    }
                    // name: {
                    //   value: control.value.oneOf.click.oneOf.selector
                    // }
                  }
                }
              };
            } else if (clickType == 'position') {
              return {
                click: {
                  position: {
                    x: control.oneOf.click.oneOf.position.x,
                    y: control.oneOf.click.oneOf.position.y
                  }
                }
              };
            } else {
              throw new Error('unsupported');
            }
          case 'cookie':
            return {
              cookie: {
                value: control.oneOf.cookie.value
              }
            };
          case 'header':
            return {
              header: {
                value: control.oneOf.header.value,
                name: control.oneOf.header.name
              }
            };
          case 'wait':
            return {
              wait: {
                element: {
                  xpath: {
                    value: control.oneOf.wait.element.xpath.value
                  }
                  // name: {
                  //   value: control.value.oneOf.click.oneOf.selector
                  // }
                }
              }
            };
          case 'select':
            return {
              select: {
                element: {
                  value: control.oneOf.select.element.value
                },
                selectValue: control.oneOf.select.selectValue
              }
            };

          case 'type':
            return {
              type: {
                element: {
                  value: control.oneOf.type.element.value
                },
                typeValue: control.oneOf.type.typeValue
              }
            };
        }
      }
    );
  }

  labelForXpath(xpath: string): string {
    if (xpath) {
      return xpath.substring(0, 25) + '...';
    } else {
      return 'Choose Element';
    }
  }

  labelForPosition(position: ɵValue<TypedFormGroup<InputMaybe<GqlDomElementInput>>['position']>): string {
    if (position.x && position.y) {
      return `(${position.x}, ${position.y})`;
    } else {
      return 'Click X/Y Coordinates';
    }
  }

  labelForBoundingBox(boundingBoxFG: FormGroup<{
    w: FormControl<number>;
    x: FormControl<number>;
    h: FormControl<number>;
    y: FormControl<number>
  }>): string {
    if (boundingBoxFG.valid) {
      const { x, y, w, h } = boundingBoxFG.value;
      return `[${x},${y},${w},${h}]`;
    } else {
      return 'Choose Bounding Box';
    }
  }

  async triggerPickBoundingBox(boundingBoxSink: FormGroup<{
    w: FormControl<number | null>;
    x: FormControl<number | null>;
    h: FormControl<number | null>;
    y: FormControl<number | null>
  }>) {
    await this.ensureScreenshotExists();
    this.view = 'screenshot';
    this.pickBoundingBoxDelegate = (boundingBox: BoundingBox | null) => {
      if (boundingBox) {
        boundingBoxSink.setValue({
          y: boundingBox.y,
          w: boundingBox.w,
          h: boundingBox.h,
          x: boundingBox.x
        });
      }
    };
  }

  triggerPickElement(xpathSink: FormControl<string>) {
    this.view = 'markup';
    this.pickElementDelegate = (xpath: string | null) => {
      if (xpath) {
        xpathSink.setValue(xpath);
      }
    };
  }

  async triggerPickPosition(positionSink: FormGroup<TypedFormGroup<GqlDomElementInput['position']>>) {
    await this.ensureScreenshotExists();
    this.view = 'screenshot';
    this.pickPositionDelegate = (position: XyPosition | null) => {
      if (position) {
        positionSink.setValue({
          x: position.x,
          y: position.y
        });
      }
    };
  }

  // async registerPickPositionDelegate(callback: (position: XyPosition | null) => void) {
  //   await this.ensureScreenshotExists();
  //   this.view = 'screenshot';
  //   this.isFullscreenMode = true;
  //   this.pickPositionDelegate = (position: XyPosition | null) => {
  //     this.isFullscreenMode = false;
  //     callback(position);
  //   };
  // }
  //
  // async registerPickBoundingBoxDelegate(callback: (boundingBox: BoundingBox | null) => void) {
  //   await this.ensureScreenshotExists();
  //   this.view = 'screenshot';
  //   this.isFullscreenMode = true;
  //   this.pickBoundingBoxDelegate = (boundingBox: BoundingBox | null) => {
  //     this.isFullscreenMode = false;
  //     callback(boundingBox);
  //   };
  // }

  setValue<T>(ctrl: FormControl<T>, value: T) {
    ctrl.setValue(value);
    ctrl.markAsTouched();
  }

  getMapperOptions(): KeyLabelOption<ResponseMapper>[] {
    return [
      {
        key: 'feed',
        label: 'Feed'
      },
      {
        key: 'fragment',
        label: 'Page Fragment'
      },
      {
        key: 'pageScreenshot',
        label: 'Full Page Screenshot'
      },
      {
        key: 'pageMarkup',
        label: 'Full Page Markup'
      },
      {
        key: 'readability',
        label: 'Readability'
      }
    ];
  }

  triggerPickFeed(feed: FormControl<NativeOrGenericFeed | null>) {
    return this.openWebsiteToFeedModal(feed);
  }

  labelForFeedMapper(feed: FormControl<NativeOrGenericFeed | null>) {
    if (feed.value?.genericFeed) {
      return 'Generic Feed';
    } else {
      if (feed.value?.nativeFeed) {
        return 'Native Feed';
      } else {
        return 'Choose a Feed';
      }
    }
  }

  // -- w2f modal --------------------------------------------------------------

  private async openWebsiteToFeedModal(feed: FormControl<NativeOrGenericFeed | null>) {
    const componentProps: TransformWebsiteToFeedComponentProps = {
      scrapeRequest: this.scrapeRequest,
      scrapeResponse: this.scrapeResponse,
      feed: feed.value
    }
    const modal = await this.modalCtrl.create({
      component: TransformWebsiteToFeedComponent,
      componentProps
    });

    await modal.present();
    const response = await modal.onDidDismiss();
    if (response.data) {
      this.mapperFg.controls.oneOf.controls.feed.setValue(response.data);
      this.changeRef.detectChanges();
    }
  }

  private getScrapeEmits(debug: boolean): ScrapeEmits {
    if (debug) {
      return {
        emit: [
          {
            types: [
              GqlScrapeEmitType.Markup,
              GqlScrapeEmitType.Readability,
              GqlScrapeEmitType.Feeds
            ],
            fragment: {
              xpath: {
                value: '/'
              }
            }
          }
        ],
        debug: {
          screenshot: true,
          console: true,
          cookies: true,
          html: true
        }
      }
    } else {
      if (this.mapperFg.valid) {
        switch (this.mapperFg.value.type) {
          case 'pageMarkup':
            return {
              emit: [],
              debug: {
                html: true
              }
            };
          case 'pageScreenshot':
            return {
              emit: [],
              debug: {
                screenshot: true
              }
            };
          case 'fragment':
            switch (this.mapperFg.value.oneOf.fragment.fragmentType) {
              case 'element':
                return {
                  emit: [{
                    types: [GqlScrapeEmitType.Markup],
                    fragment: {
                      xpath: {
                        value: this.mapperFg.value.oneOf.fragment.xpath
                      }
                    }
                  }
                  ]
                };
              case 'boundingBox':
                const bb = this.mapperFg.value.oneOf.fragment.boundingBox;
                return {
                  emit: [{
                    types: [GqlScrapeEmitType.Pixel],
                    fragment: {
                      boundingBox: {
                        w: bb.w,
                        h: bb.h,
                        x: bb.x,
                        y: bb.y
                      }
                    }
                  }
                  ]
                };
              default:
                throw new Error(`Unsupported fragmentType ${this.mapperFg.value.oneOf.fragment.fragmentType}`);
            }
          case 'readability':
            return {
              emit: [{
                types: [GqlScrapeEmitType.Readability],
                fragment: {
                  xpath: {
                    value: '/'
                  }
                }
              }]
            };
          case 'feed':
            return {
              emit: [{
                types: [GqlScrapeEmitType.Feed],
                fragment: {
                  genericFeed: this.mapperFg.value.oneOf.feed.genericFeed.selectors
                }
              }]
            };
        }
      }

    }
  }

  dismissModal() {
    return this.modalCtrl.dismiss()
  }

  applyChanges() {
    return this.modalCtrl.dismiss({
      request: this.getScrapeRequest(false),
      response: this.scrapeResponse
    });
  }
}
