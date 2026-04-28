import { Routes } from '@angular/router';
import { ConnectComponent } from './connect/connect.component';
import { BrowseComponent } from './browse/browse.component';
import { TransformComponent } from './transform/transform.component';
import { AuditComponent } from './audit/audit.component';

export const routes: Routes = [
  { path: '', redirectTo: 'connect', pathMatch: 'full' },
  { path: 'connect', component: ConnectComponent },
  { path: 'browse', component: BrowseComponent },
  { path: 'transform', component: TransformComponent },
  { path: 'audit', component: AuditComponent },
];
