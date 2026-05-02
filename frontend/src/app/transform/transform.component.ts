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
import { Router } from '@angular/router';

import { DlqApiService } from '../services/dlq-api.service';
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
    MatSnackBarModule
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
}
