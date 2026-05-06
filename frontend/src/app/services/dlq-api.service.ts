import { Injectable, inject, NgZone } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DlqMessage {
  topic: string;
  partition: number;
  offset: number;
  key: string;
  value: string;
  timestamp: number;
  headers: Record<string, string>;
}
export interface TransformPreviewResponse {
  input: string;
  output: string | null;
  valid: boolean;
  error: string | null;
}
export interface TransformTemplate {
  id?: string;
  name: string;
  expression: string;
}

@Injectable({
  providedIn: 'root'
})
export class DlqApiService {
  private http = inject(HttpClient);
  private zone = inject(NgZone);
  private baseUrl = 'http://localhost:8080/dlq';

  getMessages(
    bootstrapServers: string,
    topic: string,
    partition: number = 0,
    fromOffset: number = 0,
    limit: number = 10
  ): Observable<DlqMessage[]> {
    const params = new HttpParams()
      .set('bootstrapServers', bootstrapServers)
      .set('partition', partition.toString())
      .set('fromOffset', fromOffset.toString())
      .set('limit', limit.toString());

    return this.http.get<DlqMessage[]>(`${this.baseUrl}/${topic}/messages`, { params });
  }

  previewTransform(expression: string, sampleMessage: string): Observable<TransformPreviewResponse> {
    const payload = { expression, sampleMessage };
    return this.http.post<TransformPreviewResponse>(`${this.baseUrl}/transform/preview`, payload);
  }

  getTemplates(): Observable<TransformTemplate[]> {
    return this.http.get<TransformTemplate[]>(`${this.baseUrl}/templates`);
  }

  saveTemplate(name: string, expression: string): Observable<TransformTemplate> {
    const payload = { name, expression };
    return this.http.post<TransformTemplate>(`${this.baseUrl}/templates`, payload);
  }

  getAuditTrail(topic?: string, action?: string): Observable<AuditEntry[]> {
    let params = new HttpParams();
    if (topic) params = params.set('topic', topic);
    if (action) params = params.set('action', action);
    return this.http.get<AuditEntry[]>(`${this.baseUrl}/audit`, { params });
  }

  executeRedrive(request: RedriveRequest): Observable<RedriveSummary> {
    return this.http.post<RedriveSummary>(`${this.baseUrl}/redrive`, request);
  }

  streamMessages(
    bootstrapServers: string,
    topic: string,
    partition: number = 0,
    fromOffset: number = 0
  ): Observable<any> {
    return new Observable<any>(observer => {
      const url = `${this.baseUrl}/${topic}/messages/stream?bootstrapServers=${encodeURIComponent(bootstrapServers)}&partition=${partition}&fromOffset=${fromOffset}`;
      const eventSource = new EventSource(url);

      eventSource.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          this.zone.run(() => observer.next({ type: 'message', data: message }));
        } catch (e) {
          console.error('Error parsing SSE message', e);
        }
      };

      eventSource.addEventListener('connected', (event: any) => {
        this.zone.run(() => observer.next({ type: 'connected', data: event.data }));
      });

      eventSource.onerror = (error) => {
        eventSource.close();
        this.zone.run(() => observer.complete());
      };

      return () => eventSource.close();
    });
  }

  streamRedrive(request: RedriveRequest): Observable<any> {
    return new Observable<any>(observer => {
      fetch(`${this.baseUrl}/redrive/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
      }).then(response => {
        const reader = response.body?.getReader();
        const decoder = new TextDecoder();

        let buffer = '';
        const read = () => {
          reader?.read().then(({ done, value }) => {
            if (done) {
              this.zone.run(() => observer.complete());
              return;
            }

            buffer += decoder.decode(value, { stream: true });
            const parts = buffer.split('\n\n');
            buffer = parts.pop() || ''; // Keep the last partial part in the buffer

            for (const chunk of parts) {
              if (!chunk.trim()) continue;

              const lines = chunk.split('\n');
              let eventName = 'message';
              let dataStr = '';

              for (const line of lines) {
                if (line.startsWith('event:')) {
                  eventName = line.replace('event:', '').trim();
                } else if (line.startsWith('data:')) {
                  dataStr = line.replace('data:', '').trim();
                }
              }

              if (dataStr) {
                try {
                  const data = JSON.parse(dataStr);
                  this.zone.run(() => observer.next({ type: eventName, data }));
                } catch (e) {
                  console.error('Error parsing SSE data', dataStr, e);
                }
              }
            }
            read();
          }).catch(err => this.zone.run(() => observer.error(err)));
        }
        read();
      }).catch(err => this.zone.run(() => observer.error(err)));
    });
  }
}

export interface AuditEntry {
  id: number;
  action: string;
  topic: string;
  messageCount: number;
  user: string;
  timestamp: string;
  sessionId: string;
}

export interface RedriveMessage {
  topic: string;
  partition: number;
  offset: number;
  key: string | null;
  value: string;
}

export interface RedriveRequest {
  bootstrapServers: string;
  targetTopic: string;
  expression: string | null;
  messages: RedriveMessage[];
  user: string;
  sessionId: string;
}

export interface RedriveSummary {
  produced: number;
  skipped: number;
  failed: number;
  targetTopic: string;
  sessionId: string;
}
