import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { PgHealthResult } from '../models/service.model';

@Component({
  selector: 'app-pg-health-dialog',
  template: `
    <h2 mat-dialog-title>
      <mat-icon>monitor_heart</mat-icon>
      PostgreSQL Health
    </h2>
    <mat-dialog-content>
      <div class="score-row">
        <span class="score" [class]="'score-' + data.level.toLowerCase()">{{ data.score }}</span>
        <span class="level" [class]="'level-' + data.level.toLowerCase()">{{ data.level }}</span>
      </div>
      <mat-table [dataSource]="data.metrics" class="health-table">
        <ng-container matColumnDef="name">
          <mat-header-cell *matHeaderCellDef>Metric</mat-header-cell>
          <mat-cell *matCellDef="let m">{{ m.name }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="value">
          <mat-header-cell *matHeaderCellDef>Value</mat-header-cell>
          <mat-cell *matCellDef="let m"><code>{{ m.value }}</code></mat-cell>
        </ng-container>
        <ng-container matColumnDef="status">
          <mat-header-cell *matHeaderCellDef>Status</mat-header-cell>
          <mat-cell *matCellDef="let m">
            <span class="status-tag" [class]="'st-' + m.status.toLowerCase()">{{ m.status }}</span>
          </mat-cell>
        </ng-container>
        <mat-header-row *matHeaderRowDef="['name', 'value', 'status']"></mat-header-row>
        <mat-row *matRowDef="let row; columns: ['name', 'value', 'status']"></mat-row>
      </mat-table>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    h2 { display: flex; align-items: center; gap: 8px; }
    .score-row { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; justify-content: center; }
    .score { font-size: 40px; font-weight: 700; }
    .level { font-size: 16px; font-weight: 500; }
    .score-good, .level-good { color: #388e3c; }
    .score-fair, .level-fair { color: #f9a825; }
    .score-poor, .level-poor { color: #c62828; }
    .health-table { width: 100%; }
    code { font-family: monospace; }
    .status-tag { padding: 2px 8px; border-radius: 8px; font-size: 11px; font-weight: 500; text-transform: uppercase; }
    .st-ok { background: #1b5e20; color: #a5d6a7; }
    .st-warning { background: #e65100; color: #ffcc80; }
    .st-critical { background: #b71c1c; color: #ef9a9a; }
    .st-info { background: #37474f; color: #90a4ae; }
    .st-unknown { background: #4a4a4a; color: #bdbdbd; }
  `]
})
export class PgHealthDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: PgHealthResult) {}
}
