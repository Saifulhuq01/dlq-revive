import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';

import { DlqApiService, TransformTemplate, RedriveRequest, RedriveSummary } from '../services/dlq-api.service';
import { ConnectionService } from '../services/connection.service';

@Component({
  selector: 'app-transform',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatCardModule, 
    MatButtonModule, 
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatSelectModule
  ],
  templateUrl: './transform.component.html',
  styleUrl: './transform.component.scss'
})
export class TransformComponent implements OnInit {
  private router = inject(Router);
  private connectionService = inject(ConnectionService);
  private dlqApi = inject(DlqApiService);
  private snackBar = inject(MatSnackBar);

  selectedMessage: any = null;
  expression: string = '$';
  originalInput: string = '';
  transformedOutput: string = '';
  
  isValid: boolean | null = null;
  errorMsg: string | null = null;
  isLoading = false;

  templates: TransformTemplate[] = [];
  selectedTemplateId: string | null = null;
  isSaving = false;

  // Redrive state
  targetTopic: string = '';
  isRedriving = false;
  redriveSummary: RedriveSummary | null = null;
  redriveError: string | null = null;

  ngOnInit() {
    const msg = this.connectionService.getSelectedMessage();
    if (!msg) {
      this.router.navigate(['/browse']);
      return;
    }
    this.selectedMessage = msg;
    // Format JSON for readability
    try {
      this.originalInput = JSON.stringify(JSON.parse(msg.value), null, 2);
    } catch(e) {
      this.originalInput = msg.value;
    }

    this.loadTemplates();
  }

  loadTemplates() {
    this.dlqApi.getTemplates().subscribe({
      next: (data) => this.templates = data,
      error: (err) => console.error('Failed to load templates', err)
    });
  }

  onTemplateSelected(templateId: string) {
    const template = this.templates.find(t => t.id === templateId);
    if (template) {
      this.expression = template.expression;
      this.preview(); // automatically preview when loaded
    }
  }

  saveTemplate() {
    if (!this.expression.trim()) {
      this.snackBar.open('Cannot save an empty expression', 'Close', { duration: 3000 });
      return;
    }

    const name = prompt('Enter a name for this template:');
    if (!name) return;

    this.isSaving = true;
    this.dlqApi.saveTemplate(name, this.expression).subscribe({
      next: (saved) => {
        this.templates.unshift(saved);
        this.selectedTemplateId = saved.id || null;
        this.isSaving = false;
        this.snackBar.open('Template saved successfully!', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.isSaving = false;
        if (err.status === 402) {
          this.snackBar.open(err.error?.error || 'Upgrade required to save more templates.', 'Close', { duration: 5000, panelClass: ['error-snackbar'] });
        } else {
          this.snackBar.open('Failed to save template.', 'Close', { duration: 3000 });
        }
      }
    });
  }

  preview() {
    if (!this.expression.trim()) return;
    this.isLoading = true;
    this.isValid = null;
    this.errorMsg = null;

    this.dlqApi.previewTransform(this.expression, this.originalInput).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.isValid = res.valid;
        if (res.valid && res.output) {
          try {
            this.transformedOutput = JSON.stringify(JSON.parse(res.output), null, 2);
          } catch(e) {
            this.transformedOutput = res.output;
          }
          this.errorMsg = null;
        } else {
          this.transformedOutput = '';
          this.errorMsg = res.error || 'Unknown transformation error';
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.isValid = false;
        this.errorMsg = 'Failed to connect to backend';
        this.transformedOutput = '';
        console.error(err);
      }
    });
  }

  copyExpression() {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(this.expression).then(() => {
        this.snackBar.open('Expression copied to clipboard!', 'Close', { duration: 3000 });
      });
    }
  }

  executeRedrive() {
    if (!this.targetTopic.trim()) {
      this.snackBar.open('Please enter a target topic', 'Close', { duration: 3000 });
      return;
    }
    if (!this.selectedMessage) return;

    const connection = this.connectionService.getConnection();
    const request: RedriveRequest = {
      bootstrapServers: connection?.bootstrapServers || 'localhost:9092',
      targetTopic: this.targetTopic,
      expression: this.expression !== '$' ? this.expression : null,
      messages: [{
        topic: this.selectedMessage.topic,
        partition: this.selectedMessage.partition,
        offset: this.selectedMessage.offset,
        key: this.selectedMessage.key,
        value: this.selectedMessage.value
      }],
      user: 'api-user',
      sessionId: 'session-' + Date.now()
    };

    this.isRedriving = true;
    this.redriveSummary = null;
    this.redriveError = null;

    this.dlqApi.executeRedrive(request).subscribe({
      next: (summary) => {
        this.isRedriving = false;
        this.redriveSummary = summary;
        this.snackBar.open(
          `Redrive complete: Produced ${summary.produced}, Skipped ${summary.skipped}, Failed ${summary.failed}`,
          'Close', { duration: 5000 }
        );
      },
      error: (err) => {
        this.isRedriving = false;
        if (err.status === 402) {
          this.redriveError = err.error?.message || 'Free tier limit: 100 messages. Upgrade for bulk redrive.';
        } else {
          this.redriveError = 'Redrive failed: ' + (err.error?.message || err.message || 'Unknown error');
        }
      }
    });
  }
}
