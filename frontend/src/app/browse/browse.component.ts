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
import { ChangeDetectorRef } from '@angular/core';

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
  private cdr = inject(ChangeDetectorRef);

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

  private streamSubscription: any;

  loadMessages(reset = false) {
    let config = this.connectionService.getConnection();
    if (!config) {
      // Default fallback if accessed directly (use localhost:9092 since you are running via IntelliJ)
      config = { bootstrapServers: 'localhost:9092', topicName: 'payments.dlq' };
      this.connectionService.connect(config);
    }

    if (reset) {
      this.currentOffset = 0;
      this.messages = [];
      this.hasMore = false;
    }

    this.topicName = config.topicName;
    this.isLoading = true;
    this.error = null;

    if (this.streamSubscription) {
      this.streamSubscription.unsubscribe();
    }

    this.streamSubscription = this.dlqApi.streamMessages(config.bootstrapServers, config.topicName, 0, this.currentOffset).subscribe({
      next: (event) => {
        if (event.type === 'message') {
          this.messages = [...this.messages, event.data];
          this.isLoading = false;
        } else if (event.type === 'connected') {
          this.isLoading = false;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load messages stream', err);
        this.error = 'Failed to load messages from backend stream.';
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      complete: () => {
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy() {
    if (this.streamSubscription) {
      this.streamSubscription.unsubscribe();
    }
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
