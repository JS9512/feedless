import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DocumentEditorModalComponent } from './document-editor-modal.component';
import { IonicModule } from '@ionic/angular';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SearchbarModule } from '../../elements/searchbar/searchbar.module';
import { CodeEditorModule } from '../../elements/code-editor/code-editor.module';

@NgModule({
  declarations: [DocumentEditorModalComponent],
  exports: [DocumentEditorModalComponent],
  imports: [CommonModule, IonicModule, FormsModule, SearchbarModule, CodeEditorModule, ReactiveFormsModule]
})
export class DocumentEditorModalModule {}
