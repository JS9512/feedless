import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { AgentService } from '../../../services/agent.service';
import { RepositoryService } from '../../../services/repository.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-feedless-menu',
  templateUrl: './feedless-menu.component.html',
  styleUrls: ['./feedless-menu.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedlessMenuComponent implements OnInit, OnDestroy {
  private subscriptions: Subscription[] = [];
  agentCount: number;
  feedCount: number;
  loggedIn: boolean;

  constructor(private readonly agentService: AgentService,
              private readonly authService: AuthService,
              private readonly changeRef: ChangeDetectorRef,
              private readonly repositoryService: RepositoryService) {}

  async ngOnInit() {
    this.subscriptions.push(
      this.authService.authorizationChange().subscribe(authentication => {
        this.loggedIn = authentication?.loggedIn === true;
        this.changeRef.detectChanges();
      }),
      this.agentService.getAgents().subscribe((agents) => {
        this.agentCount = agents.length;
        this.changeRef.detectChanges();
      }),
      this.repositoryService.countRepositories().subscribe((repoCount) => {
        this.feedCount = repoCount;
        this.changeRef.detectChanges();
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }
}