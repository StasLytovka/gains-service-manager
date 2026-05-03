import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { PgHealthResult, PgHealthMetric } from '../models/service.model';

@Component({
  selector: 'app-pg-health-dialog',
  template: `
    <h2 mat-dialog-title>
      <mat-icon>monitor_heart</mat-icon>
      PostgreSQL Health
    </h2>
    <mat-dialog-content>
      <div class="score-row">
        <span class="score" [class]="'score-' + data.level.toLowerCase()">{{ data.score }}%</span>
        <span class="level" [class]="'level-' + data.level.toLowerCase()">{{ data.level }}</span>
      </div>

      <h3 class="section-title">System Metrics</h3>
      <mat-table [dataSource]="systemMetrics" class="health-table">
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
        <mat-header-row *matHeaderRowDef="cols"></mat-header-row>
        <mat-row *matRowDef="let row; columns: cols"></mat-row>
      </mat-table>

      <h3 class="section-title" *ngIf="dbMetrics.length">Cache Hit per Database</h3>
      <mat-table [dataSource]="dbMetrics" class="health-table" *ngIf="dbMetrics.length">
        <ng-container matColumnDef="name">
          <mat-header-cell *matHeaderCellDef>Database</mat-header-cell>
          <mat-cell *matCellDef="let m">{{ m.name.replace('Cache: ', '') }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="value">
          <mat-header-cell *matHeaderCellDef>Hit Ratio</mat-header-cell>
          <mat-cell *matCellDef="let m"><code>{{ m.value }}</code></mat-cell>
        </ng-container>
        <ng-container matColumnDef="status">
          <mat-header-cell *matHeaderCellDef>Status</mat-header-cell>
          <mat-cell *matCellDef="let m">
            <span class="status-tag" [class]="'st-' + m.status.toLowerCase()">{{ m.status }}</span>
          </mat-cell>
        </ng-container>
        <mat-header-row *matHeaderRowDef="cols"></mat-header-row>
        <mat-row *matRowDef="let row; columns: cols"></mat-row>
      </mat-table>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    h2 { display: flex; align-items: center; gap: 8px; }
    .score-row { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; justify-content: center; padding: 8px 0; }
    .score { font-size: 40px; font-weight: 700; line-height: 1; }
    .level { font-size: 16px; font-weight: 500; }
    .score-good, .level-good { color: #388e3c; }
    .score-fair, .level-fair { color: #f9a825; }
    .score-poor, .level-poor { color: #c62828; }
    .section-title { font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; opacity: 0.7; margin: 16px 0 8px; }
    .health-table { width: 100%; }
    mat-dialog-content { max-height: 70vh; }
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
  cols = ['name', 'value', 'status'];
  systemMetrics: PgHealthMetric[];
  dbMetrics: PgHealthMetric[];

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: PgHealthResult) {
    this.systemMetrics = data.metrics.filter(m => !m.name.startsWith('Cache: '));
    this.dbMetrics = data.metrics.filter(m => m.name.startsWith('Cache: '));
  }
}
