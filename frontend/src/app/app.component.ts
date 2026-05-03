import {Component, OnInit} from '@angular/core';
import {ThemeService} from './services/theme.service';

@Component({
    selector: 'app-root',
    template: `
        <router-outlet></router-outlet>`
})
export class AppComponent implements OnInit {
    constructor(private readonly theme: ThemeService) {
    }

    ngOnInit(): void {
        this.theme.applyTheme();
    }
}
