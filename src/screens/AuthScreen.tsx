import React, { useState, useEffect } from 'react';
import { UserAccountEntity, UserSession, UserRole, CompanySettings } from '../types';
import { AppDb } from '../storage/db';
import { ShieldCheck, Lock, User, Upload, AlertCircle, KeyRound } from 'lucide-react';
import { sound } from '../utils/soundEngine';

interface AuthScreenProps {
  company: CompanySettings;
  onLoginSuccess: (session: UserSession) => void;
}

export const AuthScreen: React.FC<AuthScreenProps> = ({ company, onLoginSuccess }) => {
  const [users, setUsers] = useState<UserAccountEntity[]>([]);
  const [selectedRole, setSelectedRole] = useState<UserRole>('admin');
  const [username, setUsername] = useState<string>('@TotalSolutionAdmin');
  const [password, setPassword] = useState<string>('');
  const [error, setError] = useState<string>('');
  const [failedAttempts, setFailedAttempts] = useState<number>(0);
  const [lockoutTime, setLockoutTime] = useState<number>(0);

  // First time admin setup mode
  const [isSetupMode, setIsSetupMode] = useState<boolean>(false);
  const [setupDisplayName, setSetupDisplayName] = useState<string>('Manoj Kumar Saha');
  const [setupEmail, setSetupEmail] = useState<string>('rajrudra363@gmail.com');
  const [setupPhone, setSetupPhone] = useState<string>('7083805120');

  useEffect(() => {
    const list = AppDb.getUsers();
    setUsers(list);
    const hasAdmin = list.some((u) => u.role === 'admin');
    if (!hasAdmin) {
      setIsSetupMode(true);
      setUsername('@TotalSolutionAdmin');
    }
  }, []);

  // Update prefilled username when switching role
  const handleRoleChange = (role: UserRole) => {
    setSelectedRole(role);
    setError('');
    if (role === 'admin') {
      setUsername('@TotalSolutionAdmin');
    } else if (role === 'super_admin') {
      setUsername('@superadmin');
    } else {
      setUsername('@ramesh_tech');
    }
  };

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    if (Date.now() < lockoutTime) {
      const remainingSec = Math.ceil((lockoutTime - Date.now()) / 1000);
      setError(`Account temporarily locked due to multiple failed attempts. Please retry in ${remainingSec}s.`);
      sound.playAlert();
      return;
    }

    const cleanUsername = username.trim().toLowerCase();
    const cleanPassword = password.trim();

    const user = users.find(
      (u) => u.username.toLowerCase() === cleanUsername && u.role === selectedRole
    );

    if (!user) {
      const attempts = failedAttempts + 1;
      setFailedAttempts(attempts);
      if (attempts >= 4) {
        setLockoutTime(Date.now() + 60000); // 1 minute lockout
      }
      setError(`User ${username} not found for role ${selectedRole}.`);
      sound.playAlert();
      return;
    }

    if (!user.loginEnabled) {
      setError('Your login has been disabled by Admin. Please contact your Admin.');
      sound.playAlert();
      return;
    }

    // Verify password (plain demo check or exact match)
    if (user.passwordHash !== cleanPassword && cleanPassword !== 'Total@1234') {
      const attempts = failedAttempts + 1;
      setFailedAttempts(attempts);
      if (attempts >= 4) {
        setLockoutTime(Date.now() + 60000);
      }
      setError('Incorrect password. Please verify your credentials.');
      sound.playAlert();
      return;
    }

    // Login Success!
    sound.playSuccess();
    sound.speak(`Welcome to ${company.companyName}. Login successful.`);
    onLoginSuccess({
      username: user.username,
      displayName: user.displayName,
      role: user.role,
      phone: user.phone,
      photoBase64: user.photoBase64,
      email: user.email,
      uid: user.uid,
      techId: user.techId,
      companyId: user.companyId,
      isAdmin: user.role === 'admin',
      isSuperAdmin: user.role === 'super_admin',
      isTechnician: user.role === 'technician' || user.role === 'staff',
    });
  };

  const handleSetupAdmin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!password || password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    const adminUser: UserAccountEntity = {
      username: username.startsWith('@') ? username : `@${username}`,
      displayName: setupDisplayName.trim() || 'Admin',
      passwordHash: password.trim(),
      role: 'admin',
      phone: setupPhone.trim(),
      photoBase64: '',
      email: setupEmail.trim(),
      uid: 'admin_initial',
      techId: '',
      companyId: 'total_solution',
      loginEnabled: true,
    };

    AppDb.saveUser(adminUser);
    setUsers(AppDb.getUsers());
    setIsSetupMode(false);
    setError('');
    sound.playSuccess();

    onLoginSuccess({
      username: adminUser.username,
      displayName: adminUser.displayName,
      role: 'admin',
      phone: adminUser.phone,
      photoBase64: '',
      email: adminUser.email,
      uid: adminUser.uid,
      techId: '',
      companyId: 'total_solution',
      isAdmin: true,
      isSuperAdmin: false,
      isTechnician: false,
    });
  };

  const handleBackupImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (evt) => {
      const content = evt.target?.result as string;
      if (content && AppDb.importFullBackupJson(content)) {
        sound.playSuccess();
        alert('Backup successfully imported! Please log in.');
        setUsers(AppDb.getUsers());
      } else {
        setError('Invalid backup JSON file.');
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#0F172A] via-[#1E293B] to-[#1E3A8A] flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-white rounded-3xl shadow-2xl overflow-hidden border border-slate-700/50 animate-in fade-in zoom-in duration-300">
        {/* Banner */}
        <div className="bg-[#1E3A8A] p-6 text-white text-center relative overflow-hidden">
          <div className="absolute -top-12 -right-12 w-32 h-32 bg-blue-500/20 rounded-full blur-xl pointer-events-none" />
          <div className="flex justify-center mb-3">
            <img
              src={company.logoBase64 || '/img_tse_logo.jpg'}
              alt="Logo"
              className="w-20 h-20 object-contain rounded-2xl bg-white p-1.5 shadow-lg border border-white/20"
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/ic_launcher_cctv_fg.png';
              }}
            />
          </div>
          <h2 className="text-xl font-black uppercase tracking-tight text-white">
            {company.companyName}
          </h2>
          <p className="text-xs text-blue-200 font-semibold mt-0.5">
            CCTV Security • Billing & Dynamic UPI System
          </p>
        </div>

        {/* Content */}
        <div className="p-6 sm:p-8 space-y-6">
          {error && (
            <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs font-semibold flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-rose-500" />
              <span>{error}</span>
            </div>
          )}

          {isSetupMode ? (
            /* Setup Admin Form */
            <form onSubmit={handleSetupAdmin} className="space-y-4">
              <div className="text-center">
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800">
                  <ShieldCheck className="w-3.5 h-3.5" /> First-Time Admin Setup
                </span>
                <p className="text-xs text-slate-500 mt-1">
                  Configure your primary master administrator credentials.
                </p>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Admin Username</label>
                <input
                  type="text"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm font-semibold outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Full Name</label>
                <input
                  type="text"
                  required
                  value={setupDisplayName}
                  onChange={(e) => setSetupDisplayName(e.target.value)}
                  className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm font-semibold outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Email</label>
                <input
                  type="email"
                  required
                  value={setupEmail}
                  onChange={(e) => setSetupEmail(e.target.value)}
                  className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm font-semibold outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Master Password</label>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Min 6 characters (e.g. Total@1234)"
                  className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <button
                type="submit"
                className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl shadow-lg shadow-blue-600/30 transition flex items-center justify-center gap-2"
              >
                <ShieldCheck className="w-4 h-4" />
                Initialize Admin Account
              </button>
            </form>
          ) : (
            /* Standard Login Form */
            <form onSubmit={handleLogin} className="space-y-4">
              {/* Role Tabs */}
              <div className="grid grid-cols-3 gap-1 bg-slate-100 p-1 rounded-xl">
                <button
                  type="button"
                  onClick={() => handleRoleChange('admin')}
                  className={`py-2 text-xs font-bold rounded-lg transition ${
                    selectedRole === 'admin'
                      ? 'bg-blue-600 text-white shadow'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Admin
                </button>
                <button
                  type="button"
                  onClick={() => handleRoleChange('super_admin')}
                  className={`py-2 text-xs font-bold rounded-lg transition ${
                    selectedRole === 'super_admin'
                      ? 'bg-indigo-600 text-white shadow'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Super Admin
                </button>
                <button
                  type="button"
                  onClick={() => handleRoleChange('technician')}
                  className={`py-2 text-xs font-bold rounded-lg transition ${
                    selectedRole === 'technician'
                      ? 'bg-emerald-600 text-white shadow'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Technician
                </button>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Username / ID</label>
                <div className="relative">
                  <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                  <input
                    type="text"
                    required
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="w-full pl-10 pr-4 py-2.5 border border-slate-300 rounded-xl text-sm font-semibold outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between items-center mb-1">
                  <label className="block text-xs font-bold text-slate-700">Password</label>
                  <span className="text-[10px] text-blue-600 font-semibold cursor-pointer" onClick={() => setPassword('Total@1234')}>
                    Auto-fill Demo Key
                  </span>
                </div>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                  <input
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter password..."
                    className="w-full pl-10 pr-4 py-2.5 border border-slate-300 rounded-xl text-sm outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>

              <button
                type="submit"
                className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl shadow-lg shadow-blue-600/30 transition flex items-center justify-center gap-2"
              >
                <KeyRound className="w-4 h-4" />
                Sign In to TOTAL SOLUTION
              </button>
            </form>
          )}

          {/* Backup Restore Option */}
          <div className="pt-4 border-t border-slate-200 flex flex-col items-center">
            <label className="cursor-pointer text-xs font-semibold text-slate-600 hover:text-blue-600 flex items-center gap-1.5 transition">
              <Upload className="w-3.5 h-3.5" />
              <span>Restore Data from Backup File (JSON)</span>
              <input type="file" accept=".json" onChange={handleBackupImport} className="hidden" />
            </label>
          </div>
        </div>
      </div>
    </div>
  );
};
