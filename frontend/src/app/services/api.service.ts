import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ServiceStatus, ActionResult, LogResult,
  PostgresStatus, BulkResult
} from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = `${environment.apiUrl}/services`;

  constructor(private http: HttpClient) {}

  getAllStatuses(): Observable<ServiceStatus[]> {
    return this.http.get<ServiceStatus[]>(this.base);
  }

  getStatus(username: string, serviceName: string): Observable<ServiceStatus> {
    return this.http.get<ServiceStatus>(`${this.base}/${username}/${serviceName}/status`);
  }

  start(username: string, serviceName: string): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.base}/${username}/${serviceName}/start`, {});
  }

  stop(username: string, serviceName: string): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.base}/${username}/${serviceName}/stop`, {});
  }

  restart(username: string, serviceName: string): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.base}/${username}/${serviceName}/restart`, {});
  }

  getLogs(username: string, serviceName: string, lines = 50): Observable<LogResult> {
    return this.http.get<LogResult>(`${this.base}/${username}/${serviceName}/logs?lines=${lines}`);
  }

  startAll(): Observable<BulkResult> {
    return this.http.post<BulkResult>(`${this.base}/start-all`, {});
  }

  stopAll(): Observable<BulkResult> {
    return this.http.post<BulkResult>(`${this.base}/stop-all`, {});
  }

  getPostgresStatus(): Observable<PostgresStatus> {
    return this.http.get<PostgresStatus>(`${this.base}/postgres`);
  }

  startPostgres(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.base}/postgres/start`, {});
  }

  stopPostgres(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${this.base}/postgres/stop`, {});
  }
}
