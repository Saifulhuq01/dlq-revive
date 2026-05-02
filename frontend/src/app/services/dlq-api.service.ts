import { Injectable, inject } from '@angular/core';
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

@Injectable({
  providedIn: 'root'
})
export class DlqApiService {
  private http = inject(HttpClient);
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
}
