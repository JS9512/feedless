import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { IonicModule } from '@ionic/angular';
import { UpcomingFooterComponent } from './upcoming-footer.component';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DarkModeButtonModule } from '../../../components/dark-mode-button/dark-mode-button.module';
import { MapModule } from '../../../components/map/map.module';

@NgModule({
  imports: [
    CommonModule,
    IonicModule,
    ReactiveFormsModule,
    RouterLink,
    DarkModeButtonModule,
    MapModule,
  ],
  declarations: [UpcomingFooterComponent],
  exports: [UpcomingFooterComponent],
})
export class UpcomingFooterModule {}
