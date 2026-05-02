import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  title = 'DLQ Revive';

  navItems = [
    { label: 'Connect', icon: 'electrical_plug', route: '/connect' },
    { label: 'Browse', icon: 'table_rows', route: '/browse' },
    { label: 'Transform', icon: 'transform', route: '/transform' },
    { label: 'Audit', icon: 'history', route: '/audit' },
  ];
}
