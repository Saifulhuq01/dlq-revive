import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ConnectionService } from '../services/connection.service';

@Component({
  selector: 'app-connect',
  standalone: true,
  imports: [
    CommonModule, 
    ReactiveFormsModule,
    MatCardModule, 
    MatButtonModule, 
    MatIconModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './connect.component.html',
  styleUrl: './connect.component.scss'
})
export class ConnectComponent {
  title = 'Connect to Kafka';
  
  private fb = inject(FormBuilder);
  private connectionService = inject(ConnectionService);
  private router = inject(Router);

  connectForm = this.fb.group({
    bootstrapServers: ['localhost:9092', Validators.required],
    topicName: ['dlq-events', Validators.required]
  });

  onSubmit() {
    if (this.connectForm.valid) {
      const { bootstrapServers, topicName } = this.connectForm.value;
      this.connectionService.connect({
        bootstrapServers: bootstrapServers!,
        topicName: topicName!
      });
      this.router.navigate(['/browse']);
    }
  }
}
