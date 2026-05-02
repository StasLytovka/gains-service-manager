import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-log-dialog',
  template: `
    <h2 mat-dialog-title>
      <mat-icon>terminal</mat-icon>
      {{ data.username }} / {{ data.serviceName }}
    </h2>
    <mat-dialog-content>
      <div class="controls">
        <mat-form-field appearance="outline" style="width:120px">
          <mat-label>Lines</mat-label>
          <mat-select [(value)]="lines" (selectionChange)="loadLogs()">
            <mat-option [value]="30">30</mat-option>
            <mat-option [value]="50">50</mat-option>
            <mat-option [value]="100">100</mat-option>
            <mat-option [value]="200">200</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-icon-button (click)="loadLogs()" [disabled]="loading" matTooltip="Refresh">
          <mat-icon>refresh</mat-icon>
        </button>
      </div>
      <div *ngIf="loading" style="text-align:center;padding:24px">
        <mat-spinner diameter="36"></mat-spinner>
      </div>
      <div class="log-viewer" *ngIf="!loading">{{ logText }}</div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    h2 { display:flex; align-items:center; gap:8px; }
    .controls { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
    mat-dialog-content { min-height: 300px; }
  `]
})
export class LogDialogComponent implements OnInit {
  lines   = 50;
  loading = true;
  logText = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { username: string; serviceName: string },
    public dialogRef: MatDialogRef<LogDialogComponent>,
    private api: ApiService
  ) {}

  ngOnInit(): void { this.loadLogs(); }

  loadLogs(): void {
    this.loading = true;
    this.api.getLogs(this.data.username, this.data.serviceName, this.lines).subscribe({
      next: res => {
        this.logText = res.lines.join('\n') || '(no log output)';
        this.loading = false;
      },
      error: () => {
        this.logText = 'Failed to load logs';
        this.loading = false;
      }
    });
  }
}
