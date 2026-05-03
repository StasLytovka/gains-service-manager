import { Component, OnInit, OnDestroy } from '@angular/core';
import { interval, Subject, takeUntil } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../services/api.service';
import { AuthService } from '../services/auth.service';
import { ThemeService } from '../services/theme.service';
import { ServiceStatus, PostgresStatus } from '../models/service.model';
import { LogDialogComponent } from './log-dialog.component';
import { ConfirmDialogComponent } from './confirm-dialog.component';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  services: ServiceStatus[] = [];
  postgres: PostgresStatus = { status: 'unknown', message: '' };
  loading       = true;
  bulkLoading   = false;
  refreshing    = false;
  pgLoading     = false;

  displayedColumns = ['username', 'serviceName', 'port', 'status', 'pid', 'since', 'actions'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api:    ApiService,
    public  readonly auth:   AuthService,
    public  readonly theme:  ThemeService,
    private readonly dialog: MatDialog,
    private readonly snack:  MatSnackBar
  ) {}

  get pgActive(): boolean {
    return this.postgres.status === 'active';
  }

  ngOnInit(): void {
    this.loadAll();
    interval(30_000).pipe(takeUntil(this.destroy$))
      .subscribe(() => this.refreshStatuses());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadAll(): void {
    this.loading = true;
    this.api.getAllStatuses().subscribe({
      next: data => {
        this.services = data;
        this.loading  = false;
      },
      error: () => {
        this.loading = false;
        this.snack.open('Failed to load services', 'Close', { duration: 3000 });
      }
    });
    this.api.getPostgresStatus().subscribe({
      next: pg => this.postgres = pg
    });
  }

  refreshStatuses(): void {
    this.refreshing = true;
    this.api.getAllStatuses().subscribe({
      next: data => {
        this.services  = data;
        this.refreshing = false;
      },
      error: () => { this.refreshing = false; }
    });
    this.api.getPostgresStatus().subscribe({
      next: pg => this.postgres = pg
    });
  }

  // --- PostgreSQL controls ---

  startPostgres(): void {
    this.pgLoading = true;
    this.api.startPostgres().subscribe({
      next: res => {
        this.pgLoading = false;
        this.snack.open(
          res.success ? 'PostgreSQL started' : res.message,
          'Close', { duration: 3000 }
        );
        this.api.getPostgresStatus().subscribe(pg => this.postgres = pg);
      },
      error: () => {
        this.pgLoading = false;
        this.snack.open('Failed to start PostgreSQL', 'Close', { duration: 4000 });
      }
    });
  }

  stopPostgres(): void {
    const running = this.activeCount();
    if (running > 0) {
      const ref = this.dialog.open(ConfirmDialogComponent, {
        width: '440px',
        data: {
          title: 'Stop PostgreSQL?',
          message: `${running} service(s) are currently running and depend on PostgreSQL. ` +
                   `They will be stopped first. Continue?`,
          confirmText: 'Stop All & PostgreSQL',
          confirmColor: 'warn'
        }
      });
      ref.afterClosed().subscribe(confirmed => {
        if (!confirmed) return;
        this.pgLoading = true;
        this.bulkLoading = true;
        this.api.stopAll().subscribe({
          next: () => {
            this.bulkLoading = false;
            this.doStopPostgres('All services & PostgreSQL stopped');
          },
          error: () => {
            this.bulkLoading = false;
            this.pgLoading = false;
            this.snack.open('Failed to stop services', 'Close', { duration: 4000 });
          }
        });
      });
    } else {
      this.pgLoading = true;
      this.doStopPostgres('PostgreSQL stopped');
    }
  }

  private doStopPostgres(successMsg: string): void {
    this.api.stopPostgres().subscribe({
      next: res => {
        this.pgLoading = false;
        this.snack.open(res.success ? successMsg : res.message, 'Close', { duration: 4000 });
        this.refreshStatuses();
      },
      error: () => {
        this.pgLoading = false;
        this.snack.open('Failed to stop PostgreSQL', 'Close', { duration: 4000 });
        this.refreshStatuses();
      }
    });
  }

  // --- Service actions ---

  actionService(svc: ServiceStatus, action: 'start' | 'stop' | 'restart'): void {
    if (action === 'start' && !this.pgActive) {
      this.snack.open('Cannot start service: PostgreSQL is not running', 'Close', { duration: 4000 });
      return;
    }
    (svc as any)['_loading'] = true;
    const obs = action === 'start'   ? this.api.start(svc.username, svc.serviceName)
              : action === 'stop'    ? this.api.stop(svc.username, svc.serviceName)
              :                        this.api.restart(svc.username, svc.serviceName);

    obs.subscribe({
      next: res => {
        (svc as any)['_loading'] = false;
        this.snack.open(
          res.success ? `${action} → OK` : res.message,
          'Close',
          { duration: 3000, panelClass: res.success ? [] : ['snack-error'] }
        );
        setTimeout(() => this.refreshSingle(svc), 2000);
      },
      error: err => {
        (svc as any)['_loading'] = false;
        this.snack.open(`Error: ${err.error?.message ?? 'unknown'}`, 'Close', { duration: 4000 });
      }
    });
  }

  refreshSingle(svc: ServiceStatus): void {
    this.api.getStatus(svc.username, svc.serviceName).subscribe({
      next: updated => {
        const idx = this.services.findIndex(
          s => s.username === svc.username && s.serviceName === svc.serviceName
        );
        if (idx !== -1) this.services[idx] = updated;
      }
    });
  }

  openLogs(svc: ServiceStatus): void {
    this.dialog.open(LogDialogComponent, {
      width: '900px',
      maxWidth: '95vw',
      data: { username: svc.username, serviceName: svc.serviceName }
    });
  }

  // --- Bulk actions ---

  startAll(): void {
    if (!this.pgActive) {
      this.snack.open('Cannot start services: PostgreSQL is not running', 'Close', { duration: 4000 });
      return;
    }
    this.bulkLoading = true;
    this.api.startAll().subscribe({
      next: res => {
        this.bulkLoading = false;
        this.snack.open(`Start all: ${res.successCount} OK / ${res.failCount} failed`, 'Close', { duration: 4000 });
        setTimeout(() => this.refreshStatuses(), 3000);
      },
      error: () => { this.bulkLoading = false; }
    });
  }

  stopAll(): void {
    this.bulkLoading = true;
    this.api.stopAll().subscribe({
      next: res => {
        this.bulkLoading = false;
        this.snack.open(`Stop all: ${res.successCount} OK / ${res.failCount} failed`, 'Close', { duration: 4000 });
        setTimeout(() => this.refreshStatuses(), 2000);
      },
      error: () => { this.bulkLoading = false; }
    });
  }

  activeCount(): number   { return this.services.filter(s => s.status === 'active').length; }
  inactiveCount(): number { return this.services.filter(s => s.status !== 'active' && s.status !== 'error').length; }

  formatSince(since: string): string {
    if (!since) return '';
    return since.substring(0, 19).replace('T', ' ');
  }
}
