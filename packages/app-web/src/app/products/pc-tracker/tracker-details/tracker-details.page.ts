import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { Repository, WebDocument } from '../../../graphql/types';
import { ModalController } from '@ionic/angular';

@Component({
  selector: 'app-tracker-details-page',
  templateUrl: './tracker-details.page.html',
  styleUrls: ['./tracker-details.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrackerDetailsPage implements OnInit, OnDestroy {
  busy = false;
  documents: WebDocument[];
  private subscriptions: Subscription[] = [];
  private diffImageUrl: string;
  repository: Repository;

  feedUrl: string;
  constructor(
    private readonly changeRef: ChangeDetectorRef,
    private readonly activatedRoute: ActivatedRoute,
    private readonly modalCtrl: ModalController,
  ) {}

  async ngOnInit() {
    this.subscriptions.push(
      this.activatedRoute.params.subscribe((params) => {
        if (params.trackerId) {
        }
      }),
    );
    this.changeRef.detectChanges();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
    URL.revokeObjectURL(this.diffImageUrl);
  }

  dismissModal() {
    this.modalCtrl.dismiss();
  }
}
