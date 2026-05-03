import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type GainsTheme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'gains_theme';
  private theme$ = new BehaviorSubject<GainsTheme>(this.loadTheme());

  currentTheme$ = this.theme$.asObservable();

  get isDark(): boolean {
    return this.theme$.value === 'dark';
  }

  toggle(): void {
    this.setTheme(this.theme$.value === 'dark' ? 'light' : 'dark');
  }

  applyTheme(): void {
    this.applyClass(this.theme$.value);
  }

  private setTheme(theme: GainsTheme): void {
    this.theme$.next(theme);
    localStorage.setItem(this.STORAGE_KEY, theme);
    this.applyClass(theme);
  }

  private applyClass(theme: GainsTheme): void {
    document.body.classList.remove('gains-light', 'gains-dark');
    document.body.classList.add(`gains-${theme}`);
  }

  private loadTheme(): GainsTheme {
    const stored = localStorage.getItem(this.STORAGE_KEY) as GainsTheme;
    if (stored === 'light' || stored === 'dark') return stored;
    if (window.matchMedia?.('(prefers-color-scheme: light)').matches) return 'light';
    return 'dark';
  }
}
