import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';

// Angular Material
import { MatToolbarModule }     from '@angular/material/toolbar';
import { MatCardModule }        from '@angular/material/card';
import { MatFormFieldModule }   from '@angular/material/form-field';
import { MatInputModule }       from '@angular/material/input';
import { MatButtonModule }      from '@angular/material/button';
import { MatIconModule }        from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule }       from '@angular/material/table';
import { MatSnackBarModule }    from '@angular/material/snack-bar';
import { MatDialogModule }      from '@angular/material/dialog';
import { MatTooltipModule }     from '@angular/material/tooltip';
import { MatSelectModule }      from '@angular/material/select';
import { CommonModule }         from '@angular/common';

import { AppRoutingModule }      from './app-routing.module';
import { AppComponent }          from './app.component';
import { LoginComponent }        from './login/login.component';
import { DashboardComponent }    from './dashboard/dashboard.component';
import { LogDialogComponent }    from './dashboard/log-dialog.component';
import { JwtInterceptor }        from './services/jwt.interceptor';
import { ReplacePipe }           from './pipes/replace.pipe';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    DashboardComponent,
    LogDialogComponent,
    ReplacePipe
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    CommonModule,
    ReactiveFormsModule,
    HttpClientModule,
    AppRoutingModule,
    MatToolbarModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    MatDialogModule,
    MatTooltipModule,
    MatSelectModule
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
