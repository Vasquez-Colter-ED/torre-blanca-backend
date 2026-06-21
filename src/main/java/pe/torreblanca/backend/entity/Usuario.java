/* ── Tokens ──────────────────────────────────────────────────────── */
:root {
  --navy:        #0F172A;
  --blue:        #2563EB;
  --blue-dark:   #1D4ED8;
  --blue-light:  #EFF6FF;
  --white:       #FFFFFF;
  --slate-900:   #0F172A;
  --slate-600:   #475569;
  --slate-400:   #94A3B8;
  --slate-200:   #E2E8F0;
  --slate-50:    #F8FAFC;
  --danger:      #DC2626;
  --danger-soft: #FEF2F2;
  --warning:     #92400E;
  --warning-soft:#FFFBEB;
  --success:     #065F46;
  --success-soft:#ECFDF5;
  --radius:      10px;
  --transition:  0.18s ease;
  --shadow-input: 0 0 0 3px rgba(37,99,235,0.15);
}

/* ── Layout raíz ─────────────────────────────────────────────────── */
.login-root {
  height: 100vh;
  display: flex;
  background: var(--white);
  overflow: hidden;
}

/* ── Panel izquierdo — imagen ────────────────────────────────────── */
.login-image-panel {
  width: 45%;
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
}

.login-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.login-img-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    rgba(15,23,42,0.92) 0%,
    rgba(15,23,42,0.45) 50%,
    rgba(15,23,42,0.2) 100%
  );
  display: flex;
  align-items: flex-end;
  padding: 40px;
}

.login-img-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.7);
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: 20px;
  padding: 4px 12px;
  margin-bottom: 12px;
}

.login-img-title {
  font-size: 2rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.5px;
  margin: 0 0 6px 0;
  line-height: 1.1;
}

.login-img-sub {
  font-size: 0.88rem;
  color: rgba(255,255,255,0.6);
  margin: 0;
}

/* ── Panel derecho — formulario ──────────────────────────────────── */
.login-form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 40px;
  background: var(--white);
  overflow-y: auto;
}

.login-form-inner {
  width: 100%;
  max-width: 380px;
  animation: fadeUp 0.35s ease;
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(20px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* ── Marca ───────────────────────────────────────────────────────── */
.login-brand {
  margin-bottom: 32px;
}

.login-brand-icon {
  width: 48px;
  height: 48px;
  background: var(--navy);
  color: #fff;
  font-size: 0.95rem;
  font-weight: 800;
  letter-spacing: 1px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.login-title {
  font-size: 1.65rem;
  font-weight: 800;
  color: var(--navy);
  letter-spacing: -0.5px;
  margin: 0 0 6px 0;
}

.login-subtitle {
  font-size: 0.9rem;
  color: var(--slate-600);
  margin: 0;
}

/* ── Formulario ──────────────────────────────────────────────────── */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── Campo genérico ──────────────────────────────────────────────── */
.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* Label cambia de color al enfocar — efecto Mercury */
.field-label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--slate-600);
  transition: color var(--transition);
  letter-spacing: 0.01em;
}

/* Cuando el input hermano está enfocado, el label cambia a azul */
.field-group:focus-within .field-label {
  color: var(--blue);
}

.field-label-row:focus-within .field-label {
  color: var(--blue);
}

/* Input — efecto Mercury: borde completo cambia a azul + sombra sutil */
.field-input {
  width: 100%;
  padding: 11px 14px;
  font-size: 0.95rem;
  font-family: inherit;
  color: var(--navy);
  background: var(--white);
  border: 1.5px solid var(--slate-200);
  border-radius: var(--radius);
  outline: none;
  transition:
    border-color var(--transition),
    box-shadow var(--transition);
  box-sizing: border-box;
}

.field-input::placeholder {
  color: var(--slate-400);
}

.field-input:focus {
  border-color: var(--blue);
  box-shadow: var(--shadow-input);
}

/* ── Campo contraseña con botón ver ──────────────────────────────── */
.field-password-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.field-password-wrap .field-input {
  padding-right: 44px;
}

.btn-toggle-pass {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: var(--slate-400);
  display: flex;
  align-items: center;
  transition: color var(--transition);
  line-height: 1;
}

.btn-toggle-pass:hover {
  color: var(--blue);
}

/* ── Link olvidé contraseña ──────────────────────────────────────── */
.link-olvide {
  background: none;
  border: none;
  padding: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--blue);
  cursor: pointer;
  transition: color var(--transition);
  font-family: inherit;
  text-align: left;
  margin-top: 6px;
}

.link-olvide:hover {
  color: var(--blue-dark);
  text-decoration: underline;
}

/* ── Alertas ─────────────────────────────────────────────────────── */
.login-alert {
  font-size: 0.875rem;
  font-weight: 500;
  padding: 11px 14px;
  border-radius: var(--radius);
  line-height: 1.45;
}

.login-alert-error {
  background: var(--danger-soft);
  color: var(--danger);
  border: 1px solid #FECACA;
}

.login-alert-warning {
  background: var(--warning-soft);
  color: var(--warning);
  border: 1px solid #FDE68A;
}

.login-alert-success {
  background: var(--success-soft);
  color: var(--success);
  border: 1px solid #A7F3D0;
}

/* ── Botón principal ─────────────────────────────────────────────── */
.btn-login {
  width: 100%;
  padding: 13px;
  background: var(--blue);
  color: #fff;
  font-size: 0.95rem;
  font-weight: 700;
  font-family: inherit;
  border: none;
  border-radius: var(--radius);
  cursor: pointer;
  transition: background var(--transition), transform 0.1s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn-login:hover:not(:disabled) {
  background: var(--blue-dark);
}

.btn-login:active:not(:disabled) {
  transform: scale(0.99);
}

.btn-login:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-login-loading {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Spinner */
.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  display: inline-block;
  flex-shrink: 0;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ── Footer ──────────────────────────────────────────────────────── */
.login-footer {
  font-size: 0.78rem;
  color: var(--slate-400);
  text-align: center;
  margin-top: 32px;
}

/* ── Modal recuperar contraseña ──────────────────────────────────── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15,23,42,0.55);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.modal-recuperar {
  background: var(--white);
  border-radius: 16px;
  padding: 28px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 20px 60px rgba(15,23,42,0.18);
  animation: fadeUp 0.25s ease;
}

.modal-recuperar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.modal-recuperar-title {
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--navy);
  margin: 0;
}

.modal-recuperar-close {
  background: none;
  border: none;
  font-size: 16px;
  color: var(--slate-400);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background var(--transition), color var(--transition);
}

.modal-recuperar-close:hover {
  background: var(--slate-50);
  color: var(--navy);
}

.modal-recuperar-desc {
  font-size: 0.88rem;
  color: var(--slate-600);
  margin: 0 0 20px 0;
  line-height: 1.5;
}

.modal-recuperar-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.modal-recuperar-actions .btn-login {
  flex: 1;
}

.btn-ghost {
  padding: 13px 18px;
  background: var(--slate-50);
  color: var(--slate-600);
  font-size: 0.9rem;
  font-weight: 600;
  font-family: inherit;
  border: 1.5px solid var(--slate-200);
  border-radius: var(--radius);
  cursor: pointer;
  transition: background var(--transition);
  white-space: nowrap;
}

.btn-ghost:hover {
  background: var(--slate-200);
}

/* ── Responsive ──────────────────────────────────────────────────── */
@media (max-width: 640px) {
  .login-image-panel { display: none; }
  .login-form-panel  { padding: 36px 24px; }
  .login-form-inner  { max-width: 100%; }
}

/* Landscape en celular — pantalla muy baja */
@media (max-height: 600px) {
  .login-brand { margin-bottom: 16px; }
  .login-brand-icon { width: 36px; height: 36px; font-size: 0.8rem; margin-bottom: 12px; }
  .login-title { font-size: 1.35rem; }
  .login-subtitle { display: none; }
  .login-form { gap: 14px; }
  .login-footer { margin-top: 16px; }
}

/* ── Barra de progreso recuperación ─────────────────────────────── */
.recovery-progress {
  display: flex;
  align-items: center;
  margin-bottom: 28px;
}

.progress-step {
  display: flex;
  align-items: center;
}

.progress-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--slate-200);
  color: var(--slate-400);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition), color var(--transition);
  flex-shrink: 0;
}

.progress-step-active .progress-dot {
  background: var(--blue);
  color: #fff;
}

.progress-step-done .progress-dot {
  background: #059669;
  color: #fff;
}

.progress-line {
  width: 48px;
  height: 2px;
  background: var(--slate-200);
  margin: 0 4px;
  transition: background var(--transition);
}

.progress-line-done {
  background: #059669;
}

/* ── Cajitas de dígitos ──────────────────────────────────────────── */
.digit-boxes {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin: 8px 0 4px 0;
}

.digit-box {
  width: 48px;
  height: 56px;
  text-align: center;
  font-size: 22px;
  font-weight: 800;
  color: var(--navy);
  background: var(--slate-50);
  border: 2px solid var(--slate-200);
  border-radius: 10px;
  outline: none;
  transition:
    border-color var(--transition),
    box-shadow var(--transition),
    background var(--transition);
  caret-color: var(--blue);
}

.digit-box:focus {
  border-color: var(--blue);
  box-shadow: var(--shadow-input);
  background: var(--white);
}

.digit-box-filled {
  border-color: var(--blue);
  background: var(--blue-light);
}

.digit-hint {
  font-size: 12.5px;
  color: var(--slate-400);
  text-align: center;
  margin: 8px 0 0 0;
}

/* ── Botón volver ────────────────────────────────────────────────── */
.btn-back {
  background: none;
  border: none;
  padding: 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--slate-400);
  cursor: pointer;
  font-family: inherit;
  text-align: center;
  transition: color var(--transition);
  width: 100%;
  margin-top: 4px;
}

.btn-back:hover {
  color: var(--navy);
}
