import React, { useState } from 'react';
import { CompanySettings, UserSession } from '../types';
import { AppDb } from '../storage/db';
import { sound } from '../utils/soundEngine';
import {
  Settings,
  Building,
  Upload,
  Download,
  Check,
  Shield,
  FileText,
  RotateCcw,
} from 'lucide-react';

interface SettingsScreenProps {
  settings: CompanySettings;
  user: UserSession;
  onSettingsUpdated: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  settings,
  user,
  onSettingsUpdated,
}) => {
  const [companyName, setCompanyName] = useState(settings.companyName);
  const [mobile, setMobile] = useState(settings.mobile);
  const [email, setEmail] = useState(settings.email);
  const [address, setAddress] = useState(settings.address);
  const [logoBase64, setLogoBase64] = useState(settings.logoBase64);
  const [authSigBase64, setAuthSigBase64] = useState(settings.authorisedSignatureBase64);
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleSaveCompany = (e: React.FormEvent) => {
    e.preventDefault();
    const updated: CompanySettings = {
      companyName: companyName.trim(),
      mobile: mobile.trim(),
      email: email.trim(),
      address: address.trim(),
      logoBase64,
      authorisedSignatureBase64: authSigBase64,
    };
    AppDb.saveCompanySettings(updated);
    sound.playSuccess();
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 3000);
    onSettingsUpdated();
  };

  const handleLogoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (evt) => {
      const base64 = evt.target?.result as string;
      if (base64) setLogoBase64(base64);
    };
    reader.readAsDataURL(file);
  };

  const handleSigUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (evt) => {
      const base64 = evt.target?.result as string;
      if (base64) setAuthSigBase64(base64);
    };
    reader.readAsDataURL(file);
  };

  const handleExportBackup = () => {
    sound.playClick();
    const jsonStr = AppDb.exportFullBackupJson();
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `TSE_Backup_${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    sound.playSuccess();
  };

  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (evt) => {
      const content = evt.target?.result as string;
      if (content && AppDb.importFullBackupJson(content)) {
        sound.playSuccess();
        alert('All database tables restored successfully from backup!');
        onSettingsUpdated();
      } else {
        sound.playAlert();
        alert('Invalid backup JSON file.');
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto pb-12">
      {/* Header */}
      <div className="bg-white p-4 sm:p-5 rounded-2xl shadow-sm border border-slate-200 flex justify-between items-center">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-2">
            <Settings className="w-5 h-5 text-blue-600" />
            Company Profile & System Settings
          </h2>
          <p className="text-xs text-slate-500">
            Official letterhead address, phone, authorized signatory, logo and full data backup
          </p>
        </div>
      </div>

      {savedSuccess && (
        <div className="p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs font-bold flex items-center gap-2">
          <Check className="w-4 h-4 text-emerald-600" />
          Company profile and branding settings updated successfully!
        </div>
      )}

      {/* Company Form */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200 space-y-6">
        <h3 className="font-bold text-sm text-slate-800 flex items-center gap-2">
          <Building className="w-4 h-4 text-blue-600" />
          Letterhead & Contact Information
        </h3>

        <form onSubmit={handleSaveCompany} className="space-y-4 text-xs">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block font-bold text-slate-700 mb-1">Company / Business Name *</label>
              <input
                type="text"
                required
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl font-bold text-sm text-blue-900 outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block font-bold text-slate-700 mb-1">Official Mobile / WhatsApp *</label>
              <input
                type="text"
                required
                value={mobile}
                onChange={(e) => setMobile(e.target.value)}
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl font-medium outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="block font-bold text-slate-700 mb-1">Official Email Address *</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl font-medium outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="block font-bold text-slate-700 mb-1">Registered Office Address *</label>
              <textarea
                rows={2}
                required
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl font-medium outline-none focus:ring-2 focus:ring-blue-500 leading-relaxed"
              />
            </div>
          </div>

          {/* Logos and Signatures */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-3 border-t">
            {/* Logo */}
            <div>
              <label className="block font-bold text-slate-700 mb-2">Company Logo</label>
              <div className="flex items-center gap-3">
                <img
                  src={logoBase64 || '/img_tse_logo.jpg'}
                  alt="Logo Preview"
                  className="w-16 h-16 object-contain rounded-xl border border-slate-200 bg-slate-50 p-1"
                />
                <label className="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-semibold cursor-pointer transition flex items-center gap-1.5">
                  <Upload className="w-3.5 h-3.5" />
                  <span>Upload Logo</span>
                  <input type="file" accept="image/*" onChange={handleLogoUpload} className="hidden" />
                </label>
              </div>
            </div>

            {/* Authorised Signature */}
            <div>
              <label className="block font-bold text-slate-700 mb-2">Authorised Signatory Stamp/Sign</label>
              <div className="flex items-center gap-3">
                {authSigBase64 ? (
                  <img
                    src={authSigBase64}
                    alt="Signature"
                    className="h-14 w-28 object-contain border rounded-xl bg-slate-50 p-1"
                  />
                ) : (
                  <div className="h-14 w-28 border-2 border-dashed border-slate-200 rounded-xl flex items-center justify-center text-slate-400">
                    No sign
                  </div>
                )}
                <label className="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-semibold cursor-pointer transition flex items-center gap-1.5">
                  <Upload className="w-3.5 h-3.5" />
                  <span>Upload Sign</span>
                  <input type="file" accept="image/*" onChange={handleSigUpload} className="hidden" />
                </label>
              </div>
            </div>
          </div>

          <div className="pt-3">
            <button
              type="submit"
              className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl shadow-lg shadow-blue-600/30 flex items-center gap-2 transition"
            >
              <Check className="w-4 h-4" />
              Save Company Settings
            </button>
          </div>
        </form>
      </div>

      {/* Backup & Restore Card */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200 space-y-4">
        <h3 className="font-bold text-sm text-slate-800 flex items-center gap-2">
          <Shield className="w-4 h-4 text-emerald-600" />
          Full System Backup & Disaster Recovery
        </h3>
        <p className="text-xs text-slate-500 leading-relaxed">
          Export all invoices, customers, product combos, technician records, payroll summaries, and audit logs to an encrypted offline JSON file.
        </p>

        <div className="flex flex-wrap gap-3 pt-2">
          <button
            onClick={handleExportBackup}
            className="px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow flex items-center gap-2 transition"
          >
            <Download className="w-4 h-4" />
            Export Backup (JSON)
          </button>

          <label className="px-5 py-2.5 border border-slate-300 hover:bg-slate-50 text-slate-700 font-bold text-xs rounded-xl cursor-pointer flex items-center gap-2 transition">
            <RotateCcw className="w-4 h-4 text-slate-500" />
            <span>Import Backup (JSON)</span>
            <input type="file" accept=".json" onChange={handleImportBackup} className="hidden" />
          </label>
        </div>
      </div>
    </div>
  );
};
