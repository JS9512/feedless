import { Injectable } from '@angular/core';
import { ToastController } from '@ionic/angular';

@Injectable({
  providedIn: 'root',
})
export class FileService {
  constructor() {}

  async uploadAsText(uploadEvent: Event): Promise<string> {
    return new Promise((resolve) => {
      const reader = new FileReader();

      Array.from((uploadEvent.target as any).files).map((file: File) => {
        reader.onloadend = async (event) => {
          const data: ArrayBuffer | string = (event.target as any).result;
          resolve(String(data));
        };
        reader.readAsText(file);
      });
    });
  }
}
