import React, { useState } from 'react';
import { CloudWorkspaceNotification, UserSession } from '../types';
import { Bell, Send, X } from 'lucide-react';

interface NotificationCenterDialogProps {
  notifications: CloudWorkspaceNotification[];
  currentUser: UserSession;
  onSendNotification: (title: string, message: string, targetRole: string) => void;
  onDismiss: () => void;
}

export const NotificationCenterDialog: React.FC<NotificationCenterDialogProps> = ({
  notifications,
  currentUser,
  onSendNotification,
  onDismiss,
}) => {
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !message.trim()) return;
    const target = currentUser.isAdmin ? 'all' : 'admin';
    onSendNotification(title, message, target);
    setTitle('');
    setMessage('');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200">
        <div className="bg-gradient-to-r from-blue-900 to-indigo-900 text-white p-5 flex justify-between items-center">
          <div className="flex items-center gap-2.5">
            <Bell className="w-5 h-5 text-blue-300" />
            <h3 className="font-bold text-lg">Team Notifications ({notifications.length})</h3>
          </div>
          <button
            onClick={onDismiss}
            className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {/* Send Box */}
          <form onSubmit={handleSend} className="bg-slate-50 border border-slate-200 rounded-xl p-3.5 space-y-2.5">
            <span className="text-xs font-bold text-slate-700 block">
              {currentUser.isAdmin ? 'Broadcast Team Alert' : 'Send Message to Admin'}
            </span>
            <input
              type="text"
              placeholder="Title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3 py-1.5 text-xs border border-slate-300 rounded-lg outline-none focus:ring-2 focus:ring-blue-500"
            />
            <textarea
              rows={2}
              placeholder="Message description..."
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              className="w-full px-3 py-1.5 text-xs border border-slate-300 rounded-lg outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              type="submit"
              disabled={!title.trim() || !message.trim()}
              className="w-full py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-bold text-xs rounded-lg flex items-center justify-center gap-1.5 transition"
            >
              <Send className="w-3.5 h-3.5" />
              Send Alert
            </button>
          </form>

          {/* List */}
          <div className="max-h-64 overflow-y-auto space-y-2 pr-1">
            {notifications.length === 0 ? (
              <p className="text-xs text-slate-500 text-center py-6 italic">
                No notifications yet.
              </p>
            ) : (
              notifications.map((n) => (
                <div key={n.id} className="p-3 bg-white border border-slate-200 rounded-xl text-xs space-y-1">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-slate-800">{n.title}</span>
                    <span className="text-[10px] text-slate-400">
                      {new Date(n.timestamp).toLocaleDateString('en-IN', {
                        day: 'numeric',
                        month: 'short',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                  </div>
                  <p className="text-slate-600 text-[11px] leading-relaxed">{n.message}</p>
                  <div className="text-[9.5px] text-blue-600 font-semibold">From: {n.sender}</div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
