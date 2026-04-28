import { Injectable, signal } from '@angular/core';

export interface ConnectionConfig {
  bootstrapServers: string;
  topicName: string;
}

@Injectable({
  providedIn: 'root'
})
export class ConnectionService {
  private currentConnection = signal<ConnectionConfig | null>(null);

  connect(config: ConnectionConfig) {
    this.currentConnection.set(config);
    console.log('Connected to:', config);
  }

  getConnection() {
    return this.currentConnection();
  }
}
