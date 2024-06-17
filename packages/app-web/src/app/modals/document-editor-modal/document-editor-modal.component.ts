import { Component } from '@angular/core';
import { ModalController } from '@ionic/angular';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import dayjs from 'dayjs';
import { WebDocument } from '../../graphql/types';

export interface DocumentEditorModalComponentProps {

}

@Component({
  selector: 'app-article-editor-modal',
  templateUrl: './document-editor-modal.component.html',
  styleUrls: ['./document-editor-modal.component.scss'],
})
export class DocumentEditorModalComponent implements DocumentEditorModalComponentProps {

  protected formFg = new FormGroup({
    title: new FormControl<string>('', [Validators.required, Validators.minLength(3)]),
    url: new FormControl<string>('', [Validators.required, Validators.minLength(3)]),
    publishedAt: new FormControl<Date>(new Date(), [Validators.required]),
    content: new FormControl<string>(`Your message \ngoes\nhere`, [Validators.maxLength(500)]),
  });

  constructor(private readonly modalCtrl: ModalController) {}

  closeModal() {
    return this.modalCtrl.dismiss();
  }

  async saveDocument() {
    if (this.formFg.valid) {
      const document: WebDocument = {
        contentTitle: this.formFg.value.title,
        contentText: this.formFg.value.content,
        publishedAt: this.formFg.value.publishedAt,
        createdAt: new Date(),
        id: '123',
        url: this.formFg.value.url,
      };
      await this.modalCtrl.dismiss(document)
    }
    this.formFg.markAllAsTouched();
  }

  // async saveDocumentDraft() {
  //   const document: WebDocument = {
  //     isDraft: true
  //   };
  //   await this.modalCtrl.dismiss(document)
  // }

  isPublishedInFuture() {
    return dayjs().isAfter(this.formFg.value.publishedAt);
  }
}
