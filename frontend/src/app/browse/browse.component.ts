import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DlqApiService, DlqMessage } from '../services/dlq-api.service';
import { ConnectionService } from '../services/connection.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-browse',
  standalone: true,
  imports: [
    CommonModule, 
    MatCardModule, 
    MatButtonModule, 
    MatIconModule, 
    MatTableModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './browse.component.html',
  styleUrl: './browse.component.scss'
})
export class BrowseComponent implements OnInit {
  private dlqApi = inject(DlqApiService);
  private connectionService = inject(ConnectionService);
  private router = inject(Router);

  messages: DlqMessage[] = [];
  displayedColumns: string[] = ['offset', 'partition', 'key', 'value', 'timestamp'];
  isLoading = false;
  error: string | null = null;
  topicName = '';

  ngOnInit() {
    this.loadMessages();
  }

  loadMessages() {
    const config = this.connectionService.getConnection();
    if (!config) {
      this.router.navigate(['/connect']);
      return;
    }

    this.topicName = config.topicName;
    this.isLoading = true;
    this.error = null;

    this.dlqApi.getMessages(config.bootstrapServers, config.topicName).subscribe({
      next: (data) => {
        this.messages = data;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load messages', err);
        this.error = 'Failed to load messages from backend.';
        this.isLoading = false;
      }
    });
  }

  truncateValue(value: string | null): string {
    if (!value) return '';
    return value.length > 100 ? value.substring(0, 100) + '...' : value;
  }
}
