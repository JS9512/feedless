import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { BucketPage } from './bucket.page';

const routes: Routes = [
  {
    path: '',
    component: BucketPage,
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class BucketPageRoutingModule {}
