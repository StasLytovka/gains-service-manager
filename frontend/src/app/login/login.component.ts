import {Component} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {AuthService} from '../services/auth.service';
import {ThemeService} from '../services/theme.service';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss']
})
export class LoginComponent {
    form: FormGroup;
    loading = false;
    error = '';
    hidePassword = true;

    constructor(
        fb: FormBuilder,
        private readonly auth: AuthService,
        private readonly router: Router,
        public readonly theme: ThemeService
    ) {
        this.form = fb.group({
            username: ['', [Validators.required]],
            password: ['', [Validators.required]]
        });
    }

    submit(): void {
        if (this.form.invalid) return;
        this.loading = true;
        this.error = '';

        const {username, password} = this.form.value;
        this.auth.login(username, password).subscribe({
            next: () => this.router.navigate(['/dashboard']),
            error: err => {
                this.loading = false;
                this.error = err.error?.error ?? 'Authentication failed';
            }
        });
    }
}
