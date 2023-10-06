import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { GqlNativeFeedStatus } from '../../../generated/graphql';
import { BasicNativeFeed } from '../../graphql/types';
import { Router } from '@angular/router';

@Component({
  selector: 'app-native-feed-ref',
  templateUrl: './native-feed-ref.component.html',
  styleUrls: ['./native-feed-ref.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NativeFeedRefComponent {
  @Input()
  feed: BasicNativeFeed;
  @Input()
  showTag = true;

  constructor(private readonly router: Router) {}

  hasProblems(status: GqlNativeFeedStatus): boolean {
    return [
      GqlNativeFeedStatus.NotFound,
      GqlNativeFeedStatus.NeverFetched,
      GqlNativeFeedStatus.Defective,
    ].includes(status);
  }

  dragFeed(dragEvent: DragEvent) {
    dragEvent.dataTransfer.setData('text', `foo`);
  }

  goTo(url: string) {
    return this.router.navigateByUrl(url);
  }
}
