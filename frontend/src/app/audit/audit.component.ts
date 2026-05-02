import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { DlqApiService, AuditEntry } from '../services/dlq-api.service';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss'
})
export class AuditComponent implements OnInit {
  private dlqApi = inject(DlqApiService);

  displayedColumns = ['id', 'action', 'topic', 'messageCount', 'user', 'timestamp', 'sessionId'];
  auditEntries: AuditEntry[] = [];
  isLoading = false;

  filterTopic: string = '';
  filterAction: string = '';

  ngOnInit() {
    this.loadAuditTrail();
  }

  loadAuditTrail() {
    this.isLoading = true;
    const topic = this.filterTopic.trim() || undefined;
    const action = this.filterAction || undefined;

    this.dlqApi.getAuditTrail(topic, action).subscribe({
      next: (data) => {
        this.auditEntries = data;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load audit trail', err);
        this.isLoading = false;
      }
    });
  }

  applyFilter() {
    this.loadAuditTrail();
  }

  clearFilters() {
    this.filterTopic = '';
    this.filterAction = '';
    this.loadAuditTrail();
  }
}
