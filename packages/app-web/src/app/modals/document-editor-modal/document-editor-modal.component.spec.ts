import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { DocumentEditorModalComponent } from './document-editor-modal.component';
import { DocumentEditorModalModule } from './document-editor-modal.module';

describe('ExportModalComponent', () => {
  let component: DocumentEditorModalComponent;
  let fixture: ComponentFixture<DocumentEditorModalComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      imports: [DocumentEditorModalModule],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentEditorModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
