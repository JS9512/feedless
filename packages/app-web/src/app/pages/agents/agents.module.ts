import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { AgentsPageRoutingModule } from './agents-routing.module';

import { AgentsPage } from './agents.page';
import { AgentsModule } from '../../components/agents/agents.module';
import { FeedlessHeaderModule } from '../../components/feedless-header/feedless-header.module';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    AgentsPageRoutingModule,
    AgentsModule,
    FeedlessHeaderModule,
  ],
  declarations: [AgentsPage],
})
export class AgentsPageModule {}
