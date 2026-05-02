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
  displayedColumns: string[] = ['offset', 'partition', 'key', 'value', 'timestamp', 'actions'];
  isLoading = false;
  error: string | null = null;
  topicName = '';

  currentOffset = 0;
  pageSize = 50;
  hasMore = true;

  ngOnInit() {
    this.loadMessages(true);
  }

  loadMessages(reset = false) {
    const config = this.connectionService.getConnection();
    if (!config) {
      this.router.navigate(['/connect']);
      return;
    }

    if (reset) {
      this.currentOffset = 0;
      this.messages = [];
      this.hasMore = true;
    }

    this.topicName = config.topicName;
    this.isLoading = true;
    this.error = null;

    this.dlqApi.getMessages(config.bootstrapServers, config.topicName, 0, this.currentOffset, this.pageSize).subscribe({
      next: (data) => {
        if (reset) {
          this.messages = data;
        } else {
          this.messages = [...this.messages, ...data];
        }
        
        this.hasMore = data.length === this.pageSize;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load messages', err);
        this.error = 'Failed to load messages from backend.';
        this.isLoading = false;
      }
    });
  }

  loadMore() {
    this.currentOffset += this.pageSize;
    this.loadMessages();
  }

  truncateValue(value: string | null): string {
    if (!value) return '';
    return value.length > 100 ? value.substring(0, 100) + '...' : value;
  }

  transform(msg: DlqMessage) {
    this.connectionService.setSelectedMessage(msg);
    this.router.navigate(['/transform']);
  }
}
