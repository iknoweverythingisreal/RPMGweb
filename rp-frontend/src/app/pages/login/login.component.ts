import { Component, OnInit, ViewChild, ElementRef, AfterViewInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
  encapsulation: ViewEncapsulation.Emulated // Changed back to Emulated for better isolation
})
export class LoginComponent implements OnInit, AfterViewInit {
  @ViewChild('particlesContainer', { static: false }) particlesContainer!: ElementRef;
  @ViewChild('loginContainer', { static: false }) loginContainer!: ElementRef;

  // Toggle between login and register
  isLoginMode = true;

  // Form data
  loginData = {
    email: '',
    password: ''
  };

  registerData = {
    email: '',
    password: '',
    confirmPassword: '',
    name: ''
  };

  // Messages and state
  errorMessage = '';
  successMessage = '';
  isLoading = false;

  constructor(
    private authService: AuthService,
    private router: Router
  ) { }

  ngOnInit() {
    // Initialize any data if needed
  }

  ngAfterViewInit() {
    this.createParticles();
    this.addInputEnhancements();
    this.addContainerEffects();
  }

  // Create floating particles effect
  createParticles() {
    if (this.particlesContainer) {
      const particlesElement = this.particlesContainer.nativeElement;
      const particleCount = 20;

      for (let i = 0; i < particleCount; i++) {
        const particle = document.createElement('div');
        particle.className = 'particle';
        particle.style.left = Math.random() * 100 + '%';
        particle.style.animationDelay = Math.random() * 15 + 's';
        particle.style.animationDuration = (15 + Math.random() * 10) + 's';
        particlesElement.appendChild(particle);
      }
    }
  }

  // Add premium input interactions
  addInputEnhancements() {
    if (this.loginContainer) {
      const inputs = this.loginContainer.nativeElement.querySelectorAll('.form-input');

      inputs.forEach((input: HTMLInputElement) => {
        input.addEventListener('focus', (e: Event) => {
          const target = e.target as HTMLElement;
          if (target.parentElement) {
            target.parentElement.style.transform = 'scale(1.02)';
          }
        });

        input.addEventListener('blur', (e: Event) => {
          const target = e.target as HTMLElement;
          if (target.parentElement) {
            target.parentElement.style.transform = 'scale(1)';
          }
        });

        input.addEventListener('input', () => {
          this.clearMessage();
        });
      });
    }
  }

  // Add container hover effects
  addContainerEffects() {
    if (this.loginContainer) {
      const container = this.loginContainer.nativeElement;

      container.addEventListener('mouseenter', () => {
        container.style.transform = 'translateY(-2px) scale(1.01)';
      });

      container.addEventListener('mouseleave', () => {
        container.style.transform = 'translateY(0) scale(1)';
      });
    }
  }

  // Switch between login and register mode
  toggleMode() {
    this.isLoginMode = !this.isLoginMode;
    this.clearMessage();
    this.resetForms();
  }

  // Switch to specific mode
  switchToMode(mode: 'login' | 'register') {
    this.isLoginMode = mode === 'login';
    this.clearMessage();
    this.resetForms();
  }

  // Reset all forms
  resetForms() {
    this.loginData = { email: '', password: '' };
    this.registerData = { email: '', password: '', confirmPassword: '', name: '' };
  }

  // Clear messages
  clearMessage() {
    this.errorMessage = '';
    this.successMessage = '';
  }

  // Show message with auto-clear for success
  showMessage(message: string, type: 'success' | 'error') {
    if (type === 'error') {
      this.errorMessage = message;
      this.successMessage = '';
    } else {
      this.successMessage = message;
      this.errorMessage = '';

      // Auto-clear success messages after 4 seconds
      setTimeout(() => {
        this.successMessage = '';
      }, 4000);
    }
  }

  // Login function
  login() {
    this.clearMessage();
    this.isLoading = true;

    if (!this.loginData.email || !this.loginData.password) {
      this.showMessage('Please fill in all fields', 'error');
      this.isLoading = false;
      return;
    }

    this.authService.login(this.loginData).subscribe({
      next: (response) => {

        // เก็บข้อมูลผ่าน AuthService (ซึ่งจะไปเซ็ต UserService ให้)
        this.authService.storeSession(response);

        this.showMessage('Login successful! Redirecting...', 'success');
        this.isLoading = false;

        setTimeout(() => {
          if (response.status === 'PENDING') {
            this.router.navigate(['/pending-approval']);
          } else {
            this.router.navigate(['/calendar']);
          }
        }, 500);
      },

      error: (err) => {
        this.showMessage(err.error?.error || 'Invalid email or password', 'error');
        this.isLoading = false;
      }
    });
  }


  // Register function
  register() {
    this.clearMessage();
    this.isLoading = true;

    if (!this.registerData.email || !this.registerData.password ||
      !this.registerData.name || !this.registerData.confirmPassword) {
      this.showMessage('Please fill in all fields', 'error');
      this.isLoading = false;
      return;
    }

    const payload = {
      email: this.registerData.email,
      password: this.registerData.password,
      name: this.registerData.name,
      calendarColor: this.getRandomColor()
    };

    this.authService.register(payload).subscribe({

      next: (res) => {
        this.successMessage =
          'Account created successfully! Waiting for admin approval.';

        this.isLoading = false;

        // ❗ ไม่มี token → ไม่ต้อง navigate
      },

      error: (error) => {
        this.showMessage(
          error.error?.error || 'Registration failed. Email may already exist.',
          'error'
        );
        this.isLoading = false;
      }
    });
  }


  // Generate random color for new users
  getRandomColor(): string {
    const colors = [
      '#8B5CF6', '#7C3AED', '#6366F1', '#4F46E5',
      '#10B981', '#059669', '#06B6D4', '#0891B2',
      '#F59E0B', '#D97706', '#EF4444', '#DC2626'
    ];
    return colors[Math.floor(Math.random() * colors.length)];
  }

  // Keyboard shortcuts handler
  handleKeyPress(event: KeyboardEvent) {
    if (event.key === 'Enter' && !this.isLoading) {
      if (this.isLoginMode) {
        this.login();
      } else {
        this.register();
      }
    }
  }
}
