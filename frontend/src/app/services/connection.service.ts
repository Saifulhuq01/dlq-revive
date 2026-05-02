import { Injectable, signal } from '@angular/core';
import { DlqMessage } from './dlq-api.service';

export interface ConnectionConfig {
  bootstrapServers: string;
  topicName: string;
}

@Injectable({
  providedIn: 'root'
})
export class ConnectionService {
  private currentConnection = signal<ConnectionConfig | null>(null);
  private currentMessage = signal<DlqMessage | null>(null);

  connect(config: ConnectionConfig) {
    this.currentConnection.set(config);
    console.log('Connected to:', config);
  }

  getConnection() {
    return this.currentConnection();
  }

  setSelectedMessage(msg: DlqMessage | null) {
    this.currentMessage.set(msg);
  }

  getSelectedMessage() {
    return this.currentMessage();
  }
}
