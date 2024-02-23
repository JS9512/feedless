import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  OnInit,
} from '@angular/core';
import { FieldWrapper, Scalars } from '../../../generated/graphql';
import { RemoteFeedItem } from '../../graphql/types';
import { FeedService } from '../../services/feed.service';
import { dateFormat } from '../../services/profile.service';

@Component({
  selector: 'app-remote-feed-item',
  templateUrl: './remote-feed-item.component.html',
  styleUrls: ['./remote-feed-item.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemoteFeedItemComponent {
  @Input({ required: true })
  feedItem: RemoteFeedItem;

  @Input({ required: true })
  feedItemIndex: number;
  constructor() {}

  toDate(date: FieldWrapper<Scalars['Long']['output']>): Date {
    return new Date(date);
  }

  protected readonly dateFormat = dateFormat;
}
