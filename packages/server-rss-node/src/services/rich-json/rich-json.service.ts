import { Injectable, Logger } from '@nestjs/common';
import { ArticleExporter, User } from '@prisma/client';
import { PrismaService } from '../../modules/prisma/prisma.service';
import { FeedService } from '../feed/feed.service';
import { newCorrId } from '../../libs/corrId';
import { FeedRef } from '../opml/opml.service';
import { GenericFeedRule } from '../../modules/typegraphql/feeds';
import fetch from 'node-fetch';
import { compact, flatten, sortBy, split, uniq, without } from 'lodash';

export interface RootJson {
  buckets: BucketJson[];
  plugins: PluginJson[];
}
export interface ManagedSubscriptionOutputJson {
  urls: string[];
  feedItemUrlsLike: string;
  then: ManagedSubscriptionOutputJson | ManagedSubscriptionApplyRuleJson;
}
export interface ManagedSubscriptionApplyRuleJson {
  urls: string[];
  linkXPath: string;
  extendContext: string;
  contextXPath: string;
}
export interface PluginJson {
  type: 'subscriptions';
  params: any;
  exclude: string[];
  output: ManagedSubscriptionOutputJson;
}
export interface SubscriptionJson {
  title?: string;
  tags?: string[];
  htmlUrl?: string;
  filter?: string;
  xmlUrl?: string;
  query?: string;
  retention_size?: number;
  harvest_with_prerender?: boolean;
  harvest?: boolean;
  allowHarvestFailure?: boolean;
}
export interface PipelineOperationJson {
  map: string;
  plugin?: string;
  context?: string;
}
export interface TriggerJson {
  expression?: string;
  on: 'change' | 'scheduled';
}
export interface ExporterSegmentJson {
  sortField: string;
  sortAsc?: boolean;
  size: number;
  digest?: boolean;
}
export interface ExporterTargetJson {
  type: string;
  forward_errors?: boolean;
  contextJson?: string;
}
export interface ExporterJson {
  trigger?: TriggerJson;
  segment?: ExporterSegmentJson;
  targets: ExporterTargetJson[];
}
export interface BucketJson {
  title: string;
  visibility: string;
  subscriptions: SubscriptionJson[];
  pipeline?: PipelineOperationJson[];
  exporters: ExporterJson[];
}

@Injectable()
export class RichJsonService {
  private readonly logger = new Logger(RichJsonService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly feedService: FeedService,
  ) {}

  async createBucketsFromRichJson(richJson: RootJson, user: User) {
    await Promise.all(
      richJson.plugins
        .filter((plugin) => plugin.type === 'subscriptions')
        .map(async (plugin) => {
          const managedFeeds = await this.parseManagedSubscriptionPlugin(
            plugin.output,
            plugin.params,
            {},
          );
          const unique = uniq(flatten(managedFeeds));
          console.log('managedFeeds', unique);
        }),
    );
    await richJson.buckets.reduce((waitFor, bucket) => {
      return waitFor.then(async () => {
        console.log('bucket', bucket.title);
        await this.prisma.bucket.create({
          data: {
            title: bucket.title,
            owner: {
              connect: {
                id: user.id,
              },
            },
            postProcessors: {
              create: (bucket.pipeline || []).map((pipelineOperation) => ({
                type: pipelineOperation.map,
                context: pipelineOperation.context,
              })),
            },
            stream: { create: {} },
            exporters: {
              create: (bucket.exporters || []).map((exporter) => {
                return {
                  ...this.parseTrigger(exporter.trigger),
                  ...this.parseSegment(exporter.segment),
                  targets: {
                    create: exporter.targets.map((target) => {
                      return {
                        type: target.type,
                        forward_errors: target.forward_errors,
                        context: target.contextJson,
                      };
                    }),
                  },
                };
              }),
            },
            subscriptions: {
              create: await Promise.all(
                bucket.subscriptions.map(async (subscription) => {
                  const feed = await this.getFeedRefs(subscription, user.id);

                  console.log(`feed ${feed.feed_url} @ ${feed.owner} `);
                  const isPrivate = this.isPrivate(feed);
                  return {
                    owner: {
                      connect: {
                        id: user.id,
                      },
                    },
                    title: feed.title,
                    tags: feed.tags,
                    feed: {
                      create: {
                        feed_url: feed.feed_url,
                        home_page_url: feed.home_page_url,
                        domain: new URL(feed.home_page_url).host,
                        broken: feed.broken,
                        is_private: isPrivate,
                        filter: feed.filter,
                        retention_size: feed.retention_size,
                        allowHarvestFailure: feed.allowHarvestFailure,
                        harvest_site: feed.harvest_site,
                        owner: {
                          connect: {
                            id: isPrivate ? user.id : 'system',
                          },
                        },
                        stream: {
                          create: {},
                        },
                      },
                    },
                  };
                }),
              ),
            },
          },
        });
      });
    }, Promise.resolve());
  }

  private async getFeedRefs(
    subscription: SubscriptionJson,
    owner: string,
  ): Promise<FeedRef> {
    const feedRef = this.toFeedRef(subscription);
    if (subscription.xmlUrl) {
      return this.completeFromXmlUrl(subscription, feedRef);
    } else if (subscription.htmlUrl) {
      return this.completeFromHtmlUrl(subscription, feedRef, owner);
    } else if (subscription.query) {
      return this.completeWithQuery(subscription, feedRef, owner);
    } else {
      throw new Error('Outline does not point to any url/query');
    }
  }

  private async completeFromXmlUrl(
    subscription: SubscriptionJson,
    feedRef: Partial<FeedRef>,
  ): Promise<FeedRef> {
    if (!subscription.xmlUrl) {
      throw new Error('xmlUrl is undefined');
    }
    const feed = await this.feedService.getFeedForUrl(subscription.xmlUrl);
    return {
      ...feedRef,
      title: subscription.title || feed.title,
      feed_url: subscription.xmlUrl,
      home_page_url: feed.home_page_url,
      broken: false,
      owner: 'system',
      is_private: false,
    };
  }

  private async completeWithQuery(
    subscription: SubscriptionJson,
    feedRef: Partial<FeedRef>,
    owner: string,
  ): Promise<FeedRef> {
    const title = subscription.title || `Search '${subscription.query}'`;

    const feedUrl = `http://localhost:8080/api/feeds/query?q=${encodeURIComponent(
      subscription.query,
    )}`;
    return {
      ...feedRef,
      title,
      feed_url: feedUrl,
      home_page_url: feedUrl,
      owner,
      broken: false,
      is_private: false,
    };
  }

  private async completeFromHtmlUrl(
    subscription: SubscriptionJson,
    feedRef: Partial<FeedRef>,
    owner: string,
  ): Promise<FeedRef | null> {
    const htmlUrl = subscription.htmlUrl;
    if (!htmlUrl) {
      throw new Error('htmlUrl is not set');
    }

    const feeds = (
      await this.feedService
        .discoverFeedsByUrl(newCorrId(), htmlUrl)
        .catch(() => ({ nativeFeeds: [] }))
    ).nativeFeeds.filter((feed) => feed);
    if (feeds.length === 0) {
      console.warn(`-> broken, cause no feeds detected`);
      return {
        ...feedRef,
        title: subscription.title || htmlUrl,
        feed_url: htmlUrl,
        home_page_url: htmlUrl,
        broken: true,
        owner,
        is_private: true,
      };
    }
    const discoveredUrl = feeds[0];

    if (feeds.length > 1) {
      console.log(
        `-> ${discoveredUrl.feed_url} [${feeds
          .slice(1, feeds.length)
          .map((feed) => feed.feed_url)}]`,
      );
    } else {
      console.log(`-> ${discoveredUrl.feed_url}`);
    }
    const feed = feeds[0];
    return {
      ...feedRef,
      title: subscription.title || feed.title,
      feed_url: feed.feed_url,
      home_page_url: htmlUrl,
      broken: false,
      owner: 'system',
      is_private: false,
    };
  }

  private parseSegment(segment: ExporterSegmentJson): Partial<ArticleExporter> {
    if (segment) {
      return {
        segment: true,
        segment_digest: segment.digest,
        segment_sort_field: segment.sortField,
        segment_sort_asc: segment.sortAsc,
        segment_size: segment.size,
      };
    } else {
      return {
        segment: false,
      };
    }
  }

  private toFeedRef(subscription: SubscriptionJson): Partial<FeedRef> {
    if (!subscription) {
      return {};
    }
    return {
      retention_size: subscription.retention_size,
      harvest_with_prerender: subscription.harvest_with_prerender || false,
      harvest_site: subscription.harvest || true,
      allowHarvestFailure: subscription.allowHarvestFailure || true,
      tags: subscription.tags,
    };
  }

  private parseTrigger(trigger: TriggerJson) {
    if (trigger) {
      return {
        trigger_refresh_on: trigger.on,
        trigger_scheduled: trigger.expression,
      };
    } else {
      return {};
    }
  }

  private isPrivate(feed: FeedRef) {
    // todo mag fix private
    return feed.is_private;
  }

  private async parseManagedSubscriptionPlugin(
    outputParam:
      | ManagedSubscriptionOutputJson
      | ManagedSubscriptionApplyRuleJson,
    pluginParams: any,
    urlParams: any,
  ) {
    if (outputParam['linkXPath']) {
      const { linkXPath, extendContext, contextXPath, urls } =
        outputParam as ManagedSubscriptionApplyRuleJson;
      return urls.map((url) => {
        const definitiveUrl = this.applyParams(url, pluginParams, urlParams);
        return `http://localhost:8080/api/rss-proxy/json?url=${encodeURIComponent(
          definitiveUrl,
        )}&linkXPath=${encodeURIComponent(
          linkXPath,
        )}&extendContext=${encodeURIComponent(
          extendContext,
        )}&contextXPath=${encodeURIComponent(contextXPath)}`;
      });
    } else {
      const output = outputParam as ManagedSubscriptionOutputJson;
      const actualUrls = output.urls.map((url) =>
        this.applyParams(url, pluginParams, urlParams),
      );
      const probableUrls = await actualUrls.reduce(
        (waitFor, url) =>
          waitFor.then(async (feeds) => {
            try {
              const discovery = await this.feedService.discoverFeedsByUrl(
                newCorrId(),
                url,
              );
              const feedUrl = await this.findFeedAlike(
                discovery.genericFeedRules,
                output.feedItemUrlsLike,
              );
              const feed = await fetch(
                feedUrl.replace('/api/rss-proxy', '/api/rss-proxy/json'),
              ).then((res) => res.json());
              feeds.push(...feed.items.map((item) => item.url));
            } catch (e) {
              console.error(e.message);
            }
            return feeds;
          }),
        Promise.resolve([]),
      );

      const urlLooksLike = new RegExp(output.feedItemUrlsLike);

      const definitiveUrls = without(
        uniq(probableUrls).filter((url) => urlLooksLike.test(url)),
        ...actualUrls,
      );

      return await Promise.all(
        definitiveUrls.map((url) => {
          const urlParams = compact(
            split(new URL(url).search, new RegExp('\\?|&')),
          ).reduce((params, raw) => {
            const values = raw.split('=');
            params[values[0]] = values[1];
            return params;
          }, {});
          return this.parseManagedSubscriptionPlugin(
            output.then,
            pluginParams,
            urlParams,
          );
        }),
      );
    }
  }

  private applyParams(urlParam: string, pluginParams: any, urlParams: any) {
    let url = urlParam;
    Object.keys(pluginParams).forEach((param) => {
      url = url.replace('${params.' + param + '}', pluginParams[param]);
    });
    Object.keys(urlParams).forEach((param) => {
      url = url.replace('${url.' + param + '}', urlParams[param]);
    });
    this.logger.log(`applyParams ${urlParam} -> ${url}`);
    return url;
  }

  private async findFeedAlike(
    genericFeedRules: GenericFeedRule[],
    feedItemUrlsLike: string,
  ): Promise<string> {
    if (genericFeedRules.length > 0) {
      const regex = new RegExp(feedItemUrlsLike);
      const rules = sortBy<{ feedRule: GenericFeedRule; matches: number }>(
        genericFeedRules.map((feedRule) => ({
          feedRule,
          matches: feedRule.samples.filter((article) => regex.test(article.url))
            .length,
        })),
        'matches',
      );
      const bestRule = rules[rules.length - 1];
      if (bestRule.matches === 0) {
        throw new Error('best feed hast no url matches');
      }
      return bestRule.feedRule.feed_url;
    }
    throw new Error(`No gen feeds`);
  }
}
