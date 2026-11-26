import React, { useState } from 'react';
import {
  UpiSettings,
  VoiceAndNoteSettings,
  UserAccountEntity,
  ExpenseEntity,
  TrashItemEntity,
  AuditLogEntity,
} from '../types';
import { AppDb } from '../storage/db';
import { formatINR } from '../utils/rupeesWords';
import { sound } from '../utils/soundEngine';
import {
  ShieldCheck,
  CreditCard,
  Volume2,
  Trash2,
  RotateCcw,
  Plus,
  Lock,
  User,
  DollarSign,
  AlertCircle,
  Check,
  X,
  FileText,
} from 'lucide-react';

interface AdminPanelScreenProps {
  upi: UpiSettings;
  voice: VoiceAndNoteSettings;
  accounts: UserAccountEntity[];
  expenses: ExpenseEntity[];
  trashItems: TrashItemEntity[];
  auditLogs: AuditLogEntity[];
  onDataUpdated: () => void;
}

export const AdminPanelScreen: React.FC<AdminPanelScreenProps> = ({
  upi,
  voice,
  accounts,
  expenses,
  trashItems,
  auditLogs,
  onDataUpdated,
}) => {
  const [activeSubTab, setActiveSubTab] = useState<'UPI' | 'USERS' | 'EXPENSES' | 'VOICE' | 'TRASH' | 'AUDIT'>('UPI');

  // UPI Form State
  const [upiId, setUpiId] = useState(upi.upiId);
  const [payeeName, setPayeeName] = useState(upi.payeeName);
  const [upiSuccess, setUpiSuccess] = useState(false);

  // Voice Form State
  const [voiceSettings, setVoiceSettings] = useState(voice);
  const [voiceSaved, setVoiceSaved] = useState(false);

  // User Form State
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [uUsername, setUUsername] = useState('');
  const [uName, setUName] = useState('');
  const [uPassword, setUPassword] = useState('');
  const [uRole, setURole] = useState<'technician' | 'super_admin'>('technician');
  const [uPhone, setUPhone] = useState('');
  const [uEmail, setUEmail] = useState('');

  // Expense Form State
  const [expenseModalOpen, setExpenseModalOpen] = useState(false);
  const [expTitle, setExpTitle] = useState('');
  const [expCat, setExpCat] = useState('Travel');
  const [expAmount, setExpAmount] = useState<number>(0);
  const [expNote, setExpNote] = useState('');

  const handleSaveUpi = (e: React.FormEvent) => {
    e.preventDefault();
    AppDb.saveUpiSettings({ ...upi, upiId: upiId.trim(), payeeName: payeeName.trim() });
    sound.playSuccess();
    setUpiSuccess(true);
    setTimeout(() => setUpiSuccess(false), 3000);
    onDataUpdated();
  };

  const handleSaveVoice = (e: React.FormEvent) => {
    e.preventDefault();
    AppDb.saveVoiceSettings(voiceSettings);
    sound.playSuccess();
    setVoiceSaved(true);
    setTimeout(() => setVoiceSaved(false), 3000);
    onDataUpdated();
  };

  const handleTestVoice = () => {
    sound.speak(
      `Welcome to ${voiceSettings.headerNote}. Total billing announcement test.`,
      voiceSettings.voiceModelId
    );
  };

  const handleSaveUser = (e: React.FormEvent) => {
    e.preventDefault();
    if (!uUsername.trim() || !uPassword.trim() || !uName.trim()) return;

    const formattedUsername = uUsername.startsWith('@') ? uUsername.trim() : `@${uUsername.trim()}`;
    const newUser: UserAccountEntity = {
      username: formattedUsername,
      displayName: uName.trim(),
      passwordHash: uPassword.trim(),
      role: uRole,
      phone: uPhone.trim(),
      photoBase64: '',
      email: uEmail.trim(),
      uid: `user_${Date.now()}`,
      techId: uRole === 'technician' ? `EMP-${Math.floor(100 + Math.random() * 900)}` : '',
      companyId: 'total_solution',
      loginEnabled: true,
    };

    AppDb.saveUser(newUser);
    sound.playSuccess();
    setUserModalOpen(false);
    onDataUpdated();

    setUUsername('');
    setUName('');
    setUPassword('');
    setUPhone('');
    setUEmail('');
  };

  const handleToggleUserLogin = (user: UserAccountEntity) => {
    sound.playClick();
    AppDb.saveUser({ ...user, loginEnabled: !user.loginEnabled });
    onDataUpdated();
  };

  const handleDeleteUser = (username: string) => {
    if (confirm(`Remove user account ${username}?`)) {
      AppDb.deleteUser(username);
      sound.playAlert();
      onDataUpdated();
    }
  };

  const handleSaveExpense = (e: React.FormEvent) => {
    e.preventDefault();
    if (!expTitle.trim() || expAmount <= 0) return;

    const payload: ExpenseEntity = {
      id: `exp_${Date.now()}`,
      title: expTitle.trim(),
      category: expCat,
      amount: expAmount,
      date: new Date().toISOString().slice(0, 10),
      note: expNote.trim(),
      recordedBy: '@TotalSolutionAdmin',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    AppDb.saveExpense(payload);
    sound.playSuccess();
    setExpenseModalOpen(false);
    onDataUpdated();

    setExpTitle('');
    setExpAmount(0);
    setExpNote('');
  };

  const handleDeleteExpense = (id: string) => {
    if (confirm('Delete this expense entry?')) {
      AppDb.deleteExpense(id);
      sound.playAlert();
      onDataUpdated();
    }
  };

  const handleRestoreTrash = (id: string) => {
    if (AppDb.restoreTrashItem(id)) {
      sound.playSuccess();
      onDataUpdated();
    } else {
      sound.playAlert();
      alert('Unable to restore item.');
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header */}
      <div className="bg-white p-4 sm:p-5 rounded-2xl shadow-sm border border-slate-200 flex justify-between items-center">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-blue-600" />
            Administrator Control Panel
          </h2>
          <p className="text-xs text-slate-500">
            UPI QR configurations, user accounts, operational expenses, speech engine, trash retention and audit trails
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1 border-b border-slate-200">
        {[
          { id: 'UPI', label: 'Dynamic UPI Settings', icon: CreditCard },
          { id: 'USERS', label: `Staff Accounts (${accounts.length})`, icon: User },
          { id: 'EXPENSES', label: `Expenses Tracker (${expenses.length})`, icon: DollarSign },
          { id: 'VOICE', label: 'Voice Announcements', icon: Volume2 },
          { id: 'TRASH', label: `Trash Retention (${trashItems.length})`, icon: Trash2 },
          { id: 'AUDIT', label: 'Security Audit Log', icon: FileText },
        ].map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => {
                sound.playClick();
                setActiveSubTab(tab.id as any);
              }}
              className={`pb-3 px-3 text-xs font-bold border-b-2 flex items-center gap-1.5 whitespace-nowrap transition ${
                activeSubTab === tab.id
                  ? 'border-blue-600 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* SUB-TAB CONTENTS */}
      {activeSubTab === 'UPI' && (
        <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200 max-w-xl space-y-5">
          <div>
            <h3 className="font-bold text-base text-slate-900">Dynamic UPI QR Payment Settings</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Configures the merchant UPI VPA and recipient account for auto-generating QR codes on invoices.
            </p>
          </div>

          {upiSuccess && (
            <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs font-bold flex items-center gap-2">
              <Check className="w-4 h-4 text-emerald-600" />
              UPI payment settings saved successfully!
            </div>
          )}

          <form onSubmit={handleSaveUpi} className="space-y-4 text-xs">
            <div>
              <label className="block font-bold text-slate-700 mb-1">UPI ID / VPA *</label>
              <input
                type="text"
                required
                value={upiId}
                onChange={(e) => setUpiId(e.target.value)}
                placeholder="e.g. totalsolution@upi / 7083805120@paytm"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl outline-none font-mono font-bold text-blue-900 focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block font-bold text-slate-700 mb-1">Payee Business / Account Name *</label>
              <input
                type="text"
                required
                value={payeeName}
                onChange={(e) => setPayeeName(e.target.value)}
                placeholder="e.g. TOTAL SOLUTION"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl outline-none font-bold focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <button
              type="submit"
              className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl shadow-lg shadow-blue-600/30 flex items-center gap-2 transition"
            >
              <Check className="w-4 h-4" />
              Save UPI Settings
            </button>
          </form>
        </div>
      )}

      {activeSubTab === 'USERS' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h3 className="font-bold text-base text-slate-900">User Accounts & Roles</h3>
            <button
              onClick={() => {
                sound.playClick();
                setUserModalOpen(true);
              }}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow flex items-center gap-1.5 transition"
            >
              <Plus className="w-4 h-4" />
              Add User Account
            </button>
          </div>

          <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                <tr>
                  <th className="py-3 px-4">User</th>
                  <th className="py-3 px-4">Role</th>
                  <th className="py-3 px-4">Contact</th>
                  <th className="py-3 px-4 text-center">Login Access</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {accounts.map((acc) => (
                  <tr key={acc.username} className="hover:bg-slate-50 transition">
                    <td className="py-3 px-4">
                      <span className="font-bold text-slate-900 block">{acc.displayName}</span>
                      <span className="text-[10px] font-mono text-slate-400">{acc.username}</span>
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold uppercase bg-blue-100 text-blue-800">
                        {acc.role}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-600">
                      <div>{acc.phone || '—'}</div>
                      <div className="text-[10px] text-slate-400">{acc.email || '—'}</div>
                    </td>
                    <td className="py-3 px-4 text-center">
                      <button
                        onClick={() => handleToggleUserLogin(acc)}
                        className={`px-2.5 py-1 rounded-full text-[10px] font-bold transition ${
                          acc.loginEnabled
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-rose-100 text-rose-800'
                        }`}
                      >
                        {acc.loginEnabled ? 'Enabled' : 'Disabled'}
                      </button>
                    </td>
                    <td className="py-3 px-4 text-right">
                      {acc.username !== '@TotalSolutionAdmin' && (
                        <button
                          onClick={() => handleDeleteUser(acc.username)}
                          className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                          title="Delete User"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeSubTab === 'EXPENSES' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h3 className="font-bold text-base text-slate-900">Operating Expenses Tracker</h3>
            <button
              onClick={() => {
                sound.playClick();
                setExpenseModalOpen(true);
              }}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow flex items-center gap-1.5 transition"
            >
              <Plus className="w-4 h-4" />
              Add Expense
            </button>
          </div>

          <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                <tr>
                  <th className="py-3 px-4">Title / Note</th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4 text-right">Amount (₹)</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {expenses.map((exp) => (
                  <tr key={exp.id} className="hover:bg-slate-50 transition">
                    <td className="py-3 px-4">
                      <span className="font-bold text-slate-900 block">{exp.title}</span>
                      {exp.note && <span className="text-[10px] text-slate-500">{exp.note}</span>}
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-700">
                        {exp.category}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-500">{exp.date}</td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-rose-700">
                      {formatINR(exp.amount)}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => handleDeleteExpense(exp.id)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeSubTab === 'VOICE' && (
        <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200 max-w-xl space-y-5">
          <div>
            <h3 className="font-bold text-base text-slate-900">Audio Speech & Voice Announcements</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Customize voice presets and automatic sound notifications on login and invoice completions.
            </p>
          </div>

          {voiceSaved && (
            <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs font-bold flex items-center gap-2">
              <Check className="w-4 h-4 text-emerald-600" />
              Voice settings saved!
            </div>
          )}

          <form onSubmit={handleSaveVoice} className="space-y-4 text-xs">
            <div>
              <label className="block font-bold text-slate-700 mb-1">Voice Preset Tone</label>
              <select
                value={voiceSettings.voiceModelId}
                onChange={(e) => setVoiceSettings({ ...voiceSettings, voiceModelId: e.target.value })}
                className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none bg-white font-medium"
              >
                <option value="preset_clear">Clear Professional</option>
                <option value="preset_warm">Warm Deep</option>
                <option value="preset_crisp">Crisp Energetic</option>
                <option value="preset_formal">Calm Formal</option>
              </select>
            </div>

            <div>
              <label className="block font-bold text-slate-700 mb-1">Header Announcement Note</label>
              <input
                type="text"
                value={voiceSettings.headerNote}
                onChange={(e) => setVoiceSettings({ ...voiceSettings, headerNote: e.target.value })}
                className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-medium"
              />
            </div>

            <div>
              <label className="block font-bold text-slate-700 mb-1">Bill Completed Voice Text</label>
              <input
                type="text"
                value={voiceSettings.billCompletedVoice}
                onChange={(e) => setVoiceSettings({ ...voiceSettings, billCompletedVoice: e.target.value })}
                className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-medium"
              />
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={handleTestVoice}
                className="px-4 py-2 border border-blue-400 text-blue-700 font-bold rounded-xl hover:bg-blue-50 flex items-center gap-1.5 transition"
              >
                <Volume2 className="w-4 h-4" />
                Test Voice
              </button>
              <button
                type="submit"
                className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow transition"
              >
                <Check className="w-4 h-4" />
                Save Voice Settings
              </button>
            </div>
          </form>
        </div>
      )}

      {activeSubTab === 'TRASH' && (
        <div className="space-y-4">
          <div>
            <h3 className="font-bold text-base text-slate-900">Trash Can & 24-Hour Retention</h3>
            <p className="text-xs text-slate-500">
              Soft-deleted invoices and customer records are protected here for 24 hours before expiration.
            </p>
          </div>

          <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
            {trashItems.length === 0 ? (
              <div className="p-12 text-center text-slate-400 text-xs">Trash is currently empty.</div>
            ) : (
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                  <tr>
                    <th className="py-3 px-4">Item Details</th>
                    <th className="py-3 px-4">Type</th>
                    <th className="py-3 px-4">Deleted By</th>
                    <th className="py-3 px-4">Expires In</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {trashItems.map((item) => {
                    const hoursLeft = Math.max(0, Math.ceil((item.expireAt - Date.now()) / 3600000));
                    return (
                      <tr key={item.id} className="hover:bg-slate-50 transition">
                        <td className="py-3 px-4">
                          <span className="font-bold text-slate-900 block">{item.title}</span>
                          <span className="text-[10px] text-slate-400">{item.summary}</span>
                        </td>
                        <td className="py-3 px-4 uppercase font-bold text-slate-600">{item.entityType}</td>
                        <td className="py-3 px-4 text-slate-500">{item.deletedBy}</td>
                        <td className="py-3 px-4 text-amber-700 font-bold">{hoursLeft} Hours</td>
                        <td className="py-3 px-4 text-right">
                          <button
                            onClick={() => handleRestoreTrash(item.id)}
                            className="px-3 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 font-bold rounded-lg flex items-center gap-1.5 ml-auto transition"
                          >
                            <RotateCcw className="w-3.5 h-3.5" />
                            Restore
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {activeSubTab === 'AUDIT' && (
        <div className="space-y-4">
          <div>
            <h3 className="font-bold text-base text-slate-900">Security & Operational Audit Log</h3>
            <p className="text-xs text-slate-500">
              Immutable ledger of business events, logins, updates, and deletion actions.
            </p>
          </div>

          <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-100 text-slate-700 font-bold border-b">
                <tr>
                  <th className="py-3 px-4">Timestamp</th>
                  <th className="py-3 px-4">Action</th>
                  <th className="py-3 px-4">Actor</th>
                  <th className="py-3 px-4">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-mono text-[11px]">
                {auditLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50">
                    <td className="py-2.5 px-4 text-slate-400">
                      {new Date(log.createdAt).toLocaleString('en-IN')}
                    </td>
                    <td className="py-2.5 px-4 font-bold text-blue-900">{log.action}</td>
                    <td className="py-2.5 px-4 text-slate-700">{log.actorUsername}</td>
                    <td className="py-2.5 px-4 text-slate-600 font-sans">{log.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Add User Modal */}
      {userModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 space-y-4 text-xs">
            <div className="flex justify-between items-center border-b pb-3">
              <h3 className="font-bold text-base text-slate-900">Create Staff User Account</h3>
              <button onClick={() => setUserModalOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSaveUser} className="space-y-3">
              <div>
                <label className="block font-bold text-slate-700 mb-1">Username / ID *</label>
                <input
                  type="text"
                  required
                  placeholder="@technician_id"
                  value={uUsername}
                  onChange={(e) => setUUsername(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl"
                />
              </div>
              <div>
                <label className="block font-bold text-slate-700 mb-1">Full Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Ramesh Verma"
                  value={uName}
                  onChange={(e) => setUName(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl"
                />
              </div>
              <div>
                <label className="block font-bold text-slate-700 mb-1">Password *</label>
                <input
                  type="password"
                  required
                  placeholder="Min 6 characters"
                  value={uPassword}
                  onChange={(e) => setUPassword(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl"
                />
              </div>
              <div>
                <label className="block font-bold text-slate-700 mb-1">Role</label>
                <select
                  value={uRole}
                  onChange={(e) => setURole(e.target.value as any)}
                  className="w-full px-3 py-2 border rounded-xl bg-white"
                >
                  <option value="technician">Technician / Field Staff</option>
                  <option value="super_admin">Super Admin</option>
                </select>
              </div>
              <div>
                <label className="block font-bold text-slate-700 mb-1">Phone Number</label>
                <input
                  type="tel"
                  placeholder="e.g. 9823123456"
                  value={uPhone}
                  onChange={(e) => setUPhone(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t">
                <button
                  type="button"
                  onClick={() => setUserModalOpen(false)}
                  className="px-4 py-2 border rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 text-white font-bold rounded-xl"
                >
                  Save Account
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Expense Modal */}
      {expenseModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 space-y-4 text-xs">
            <div className="flex justify-between items-center border-b pb-3">
              <h3 className="font-bold text-base text-slate-900">Record Operating Expense</h3>
              <button onClick={() => setExpenseModalOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleSaveExpense} className="space-y-3">
              <div>
                <label className="block font-bold text-slate-700 mb-1">Expense Title *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Van Fuel & Toll Charges"
                  value={expTitle}
                  onChange={(e) => setExpTitle(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl"
                />
              </div>
              <div>
                <label className="block font-bold text-slate-700 mb-1">Category</label>
                <select
                  value={expCat}
                  onChange={(e) => setExpCat(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl bg-white"
                >
                  <option value="Travel">Travel & Fuel</option>
                  <option value="Utilities">Broadband & Utilities</option>
                  <option value="Tools">Cabling Tools & Hardware</option>
                  <option value="Food">Staff Food & Tea</option>
                  <option value="Office">Office Rent & Stationery</option>
                  <option value="General">General</option>
                </select>
              </div>
              <div>
                <label className="block font-bold text-slate-700 mb-1">Amount (₹) *</label>
                <input
                  type="number"
                  step="any"
                  required
                  min="1"
                  value={expAmount}
                  onChange={(e) => setExpAmount(parseFloat(e.target.value) || 0)}
                  className="w-full px-3 py-2 border rounded-xl font-mono font-bold"
                />
              </div>
              <div>
                <label className="block font-bold text-slate-700 mb-1">Notes / Description</label>
                <textarea
                  rows={2}
                  value={expNote}
                  onChange={(e) => setExpNote(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t">
                <button
                  type="button"
                  onClick={() => setExpenseModalOpen(false)}
                  className="px-4 py-2 border rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-emerald-600 text-white font-bold rounded-xl"
                >
                  Save Expense
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
