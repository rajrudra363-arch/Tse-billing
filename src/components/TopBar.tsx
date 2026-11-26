import React, { useState, useEffect } from 'react';
import { CompanySettings, UserSession } from '../types';
import { Volume2, VolumeX, Bell, Moon, Sun, LogOut, ShieldCheck, User } from 'lucide-react';

interface TopBarProps {
  company: CompanySettings;
  session: UserSession;
  soundEnabled: boolean;
  unreadNotifications: number;
  onToggleSound: () => void;
  onOpenNotifications: () => void;
  onLogout: () => void;
}

export const TopBar: React.FC<TopBarProps> = ({
  company,
  session,
  soundEnabled,
  unreadNotifications,
  onToggleSound,
  onOpenNotifications,
  onLogout,
}) => {
  const [liveTime, setLiveTime] = useState<string>('');
  const [isNightClosed, setIsNightClosed] = useState<boolean>(false);

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setLiveTime(
        now.toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: true,
        })
      );

      // Night closure check: 12:01 AM to 6:00 AM
      const hours = now.getHours();
      const minutes = now.getMinutes();
      const totalMin = hours * 60 + minutes;
      setIsNightClosed(totalMin >= 1 && totalMin <= 360);
    };

    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <header className="no-print bg-[#1E3A8A] text-white border-b border-blue-900 shadow-md sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-3 sm:px-6 py-2.5 flex items-center justify-between gap-2 sm:gap-4">
        {/* Brand */}
        <div className="flex items-center gap-2.5 min-w-0">
          <img
            src={company.logoBase64 || '/img_tse_logo.jpg'}
            alt="TSE"
            className="w-9 h-9 sm:w-10 sm:h-10 rounded-lg object-contain bg-white/10 p-0.5 border border-white/20 shrink-0"
            onError={(e) => {
              (e.target as HTMLImageElement).src = '/ic_launcher_cctv_fg.png';
            }}
          />
          <div className="truncate">
            <div className="flex items-center gap-1.5">
              <h1 className="font-black text-sm sm:text-base tracking-tight truncate text-white">
                {company.companyName}
              </h1>
              {session.isAdmin && (
                <span className="hidden sm:inline-flex items-center gap-0.5 px-1.5 py-0.2 rounded text-[9px] font-extrabold uppercase bg-amber-400 text-slate-950">
                  <ShieldCheck className="w-2.5 h-2.5" />
                  Admin
                </span>
              )}
            </div>
            <p className="text-[10px] text-blue-200 hidden sm:block truncate">
              CCTV Security • Dynamic UPI • Payroll • Support
            </p>
          </div>
        </div>

        {/* Live Clock & Indicators */}
        <div className="flex items-center gap-2 sm:gap-4 shrink-0">
          {/* Digital Clock */}
          <div className="hidden md:flex flex-col items-end px-3 py-1 bg-blue-950/60 rounded-lg border border-blue-800/80 font-mono text-xs">
            <span className="font-bold text-cyan-300 tracking-wider">{liveTime}</span>
            {isNightClosed ? (
              <span className="text-[9px] text-amber-300 flex items-center gap-1 font-sans font-semibold">
                <Moon className="w-2.5 h-2.5" /> Night Shift Closed
              </span>
            ) : (
              <span className="text-[9px] text-emerald-300 flex items-center gap-1 font-sans font-semibold">
                <Sun className="w-2.5 h-2.5" /> Active Hours
              </span>
            )}
          </div>

          {/* Sound Toggle */}
          <button
            onClick={onToggleSound}
            title={soundEnabled ? 'Mute Sounds' : 'Enable Sounds'}
            className="p-2 rounded-lg bg-blue-900/60 hover:bg-blue-800 text-blue-200 hover:text-white transition"
          >
            {soundEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4 text-rose-300" />}
          </button>

          {/* Notification Bell */}
          <button
            onClick={onOpenNotifications}
            title="Team Notifications"
            className="relative p-2 rounded-lg bg-blue-900/60 hover:bg-blue-800 text-blue-200 hover:text-white transition"
          >
            <Bell className="w-4 h-4" />
            {unreadNotifications > 0 && (
              <span className="absolute -top-1 -right-1 px-1.5 py-0.5 text-[9px] font-bold bg-rose-500 text-white rounded-full animate-pulse">
                {unreadNotifications}
              </span>
            )}
          </button>

          {/* User Account / Role Chip */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-blue-950/70 border border-blue-800 text-xs font-semibold">
            <User className="w-3.5 h-3.5 text-cyan-400" />
            <span className="hidden sm:inline text-white truncate max-w-[120px]">
              {session.displayName || session.username}
            </span>
            <span className="text-[10px] text-blue-300 capitalize">
              ({session.role})
            </span>
          </div>

          {/* Logout */}
          <button
            onClick={onLogout}
            title="Sign Out"
            className="p-2 rounded-lg bg-rose-600/20 hover:bg-rose-600 text-rose-200 hover:text-white transition"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
};
