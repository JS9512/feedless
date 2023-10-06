import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { BasicBucket } from '../../graphql/types';
import { ImporterService } from '../../services/importer.service';

@Component({
  selector: 'app-bucket-ref',
  templateUrl: './bucket-ref.component.html',
  styleUrls: ['./bucket-ref.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketRefComponent {
  @Input()
  bucket: BasicBucket;
  constructor(private readonly importerService: ImporterService) {}

  async onDrop(event: DragEvent) {
    event.preventDefault();
    const feedId = event.dataTransfer.getData('text');
    console.log('onDrop', feedId);
    await this.importerService.createImporters({
      bucket: {
        connect: {
          id: this.bucket.id,
        },
      },
      feeds: [
        {
          connect: {
            id: feedId,
          },
        },
      ],
      protoImporter: {},
    });
  }

  onDragover(event: DragEvent) {
    console.log('onDragover', event.dataTransfer.getData('text'));
    event.preventDefault();
  }
}
