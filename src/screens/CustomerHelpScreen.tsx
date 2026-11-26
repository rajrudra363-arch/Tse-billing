import React, { useState } from 'react';
import {
  CustomerHelpEntity,
  CustomerEntity,
  UserAccountEntity,
  CompanySettings,
  UserSession,
  HelpCategory,
  HelpWorkflowStatusType,
} from '../types';
import { AppDb } from '../storage/db';
import { sound } from '../utils/soundEngine';
import { buildWhatsAppSupportUri, buildTechnicianIntroWhatsAppUri } from '../utils/qrGenerator';
import {
  LifeBuoy,
  Plus,
  Phone,
  Calendar,
  CheckCircle,
  AlertTriangle,
  Clock,
  User,
  Share2,
  Trash2,
  FileCheck,
  X,
  Check,
  Send,
} from 'lucide-react';

interface CustomerHelpScreenProps {
  helpRequests: CustomerHelpEntity[];
  customers: CustomerEntity[];
  accounts: UserAccountEntity[];
  company: CompanySettings;
  user: UserSession;
  onHelpRequestsUpdated: () => void;
  onConvertTicketToBill: (req: CustomerHelpEntity) => void;
}

export const CustomerHelpScreen: React.FC<CustomerHelpScreenProps> = ({
  helpRequests,
  customers,
  accounts,
  company,
  user,
  onHelpRequestsUpdated,
  onConvertTicketToBill,
}) => {
  const [filterCategory, setFilterCategory] = useState<string>('ALL');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  // Add Form State
  const [customerName, setCustomerName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [locationUrl, setLocationUrl] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<HelpCategory>('REGULAR_SERVICE');
  const [assignedTech, setAssignedTech] = useState('');
  const [installationDate, setInstallationDate] = useState('');

  const technicians = accounts.filter((a) => a.role === 'technician' || a.role === 'staff');

  const filtered = helpRequests.filter((r) => {
    if (filterCategory === 'ALL') return true;
    return r.category === filterCategory;
  });

  const handleCreateTicket = (e: React.FormEvent) => {
    e.preventDefault();
    if (!customerName.trim() || !description.trim()) return;

    const newTicket: CustomerHelpEntity = {
      id: 0,
      customerName: customerName.trim(),
      phone: phone.trim(),
      address: address.trim(),
      locationUrl: locationUrl.trim(),
      helpDescription: description.trim(),
      status: assignedTech ? 'ASSIGNED' : 'OPEN',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      acceptedBy: assignedTech,
      acceptedByName: accounts.find((a) => a.username === assignedTech)?.displayName || '',
      acceptedByPhone: accounts.find((a) => a.username === assignedTech)?.phone || '',
      acceptedAt: assignedTech ? Date.now() : 0,
      solvedAt: 0,
      billId: '',
      category,
      quotationStatus: category === 'QUOTATION' ? 'PENDING_APPROVAL' : '',
      installationDate,
      installationReminderSent: false,
      rejectedReason: '',
      assignedTechnician: assignedTech,
      helpTokenId: `TKN-${Math.floor(1000 + Math.random() * 9000)}`,
      quotationId: '',
      approvedQuotationJson: '',
    };

    AppDb.saveHelpRequest(newTicket);
    sound.playSuccess();
    setIsAddModalOpen(false);
    onHelpRequestsUpdated();

    // Reset
    setCustomerName('');
    setPhone('');
    setAddress('');
    setLocationUrl('');
    setDescription('');
    setAssignedTech('');
    setInstallationDate('');
  };

  const handleStatusChange = (ticket: CustomerHelpEntity, newStatus: HelpWorkflowStatusType) => {
    sound.playClick();
    const updated: CustomerHelpEntity = {
      ...ticket,
      status: newStatus,
      updatedAt: Date.now(),
      solvedAt: newStatus === 'SOLVED' || newStatus === 'RESOLVED' ? Date.now() : ticket.solvedAt,
    };
    AppDb.saveHelpRequest(updated);
    onHelpRequestsUpdated();
  };

  const handleAssignTechnician = (ticket: CustomerHelpEntity, techUsername: string) => {
    sound.playClick();
    const techObj = accounts.find((a) => a.username === techUsername);
    const updated: CustomerHelpEntity = {
      ...ticket,
      assignedTechnician: techUsername,
      acceptedBy: techUsername,
      acceptedByName: techObj?.displayName || '',
      acceptedByPhone: techObj?.phone || '',
      status: 'ASSIGNED',
      updatedAt: Date.now(),
    };
    AppDb.saveHelpRequest(updated);
    onHelpRequestsUpdated();
  };

  const handleDelete = (id: number) => {
    if (confirm('Delete this service desk ticket?')) {
      AppDb.deleteHelpRequest(id);
      sound.playAlert();
      onHelpRequestsUpdated();
    }
  };

  const handleWhatsAppTechIntro = (ticket: CustomerHelpEntity) => {
    const techName = ticket.acceptedByName || user.displayName || 'Technician';
    const uri = buildTechnicianIntroWhatsAppUri(ticket.phone, techName);
    if (uri) window.open(uri, '_blank');
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header */}
      <div className="bg-white p-4 sm:p-5 rounded-2xl shadow-sm border border-slate-200 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-2">
            <LifeBuoy className="w-5 h-5 text-blue-600" />
            Customer Service & Installation Desk ({helpRequests.length})
          </h2>
          <p className="text-xs text-slate-500">
            Emergency breakdown support, regular servicing, scheduled installations and quotation tickets
          </p>
        </div>

        <button
          onClick={() => {
            sound.playClick();
            setIsAddModalOpen(true);
          }}
          className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/30 flex items-center gap-1.5 transition"
        >
          <Plus className="w-4 h-4" />
          Create Support / Service Ticket
        </button>
      </div>

      {/* Category Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        {[
          { id: 'ALL', label: 'All Requests' },
          { id: 'REGULAR_SERVICE', label: 'Regular Service' },
          { id: 'EMERGENCY', label: '🚨 Emergency Need Help' },
          { id: 'INSTALLATION', label: 'Installation Works' },
          { id: 'QUOTATION', label: 'Quotation Inquiries' },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => {
              sound.playClick();
              setFilterCategory(tab.id);
            }}
            className={`px-3.5 py-2 text-xs font-bold rounded-xl whitespace-nowrap transition ${
              filterCategory === tab.id
                ? 'bg-blue-600 text-white shadow'
                : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tickets List */}
      <div className="space-y-3.5">
        {filtered.length === 0 ? (
          <div className="p-12 text-center bg-white rounded-2xl border border-slate-200 text-slate-400">
            <CheckCircle className="w-10 h-10 mx-auto text-emerald-400 mb-2" />
            <p className="font-semibold text-sm">No tickets under this category.</p>
          </div>
        ) : (
          filtered.map((ticket) => {
            const isEmergency = ticket.category === 'EMERGENCY';
            const isQuotation = ticket.category === 'QUOTATION';

            return (
              <div
                key={ticket.id}
                className={`bg-white p-5 rounded-2xl shadow-sm border transition space-y-3.5 ${
                  isEmergency
                    ? 'border-rose-400 ring-2 ring-rose-500/10'
                    : 'border-slate-200 hover:border-blue-400'
                }`}
              >
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-mono font-bold text-xs text-blue-900 bg-blue-50 px-2 py-0.5 rounded">
                        #{ticket.id} • {ticket.helpTokenId}
                      </span>
                      <span
                        className={`px-2 py-0.5 text-[10px] font-extrabold uppercase rounded-full ${
                          isEmergency
                            ? 'bg-rose-100 text-rose-800 animate-pulse'
                            : isQuotation
                            ? 'bg-indigo-100 text-indigo-800'
                            : 'bg-slate-100 text-slate-700'
                        }`}
                      >
                        {ticket.category.replace(/_/g, ' ')}
                      </span>
                      <span
                        className={`px-2 py-0.5 text-[10px] font-extrabold uppercase rounded-full ${
                          ticket.status === 'SOLVED' || ticket.status === 'RESOLVED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : ticket.status === 'OPEN'
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-blue-100 text-blue-800'
                        }`}
                      >
                        {ticket.status.replace(/_/g, ' ')}
                      </span>
                      <span className="text-[11px] text-slate-400">
                        {new Date(ticket.createdAt).toLocaleDateString('en-IN', {
                          day: 'numeric',
                          month: 'short',
                        })}
                      </span>
                    </div>

                    <h3 className="font-bold text-base text-slate-900 mt-1">
                      {ticket.customerName}
                    </h3>
                  </div>

                  {/* Convert to Bill Action */}
                  <button
                    onClick={() => onConvertTicketToBill(ticket)}
                    className="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-800 border border-indigo-200 text-xs font-bold rounded-xl flex items-center gap-1.5 transition"
                  >
                    <FileCheck className="w-3.5 h-3.5 text-indigo-600" />
                    Convert to Bill / Invoice
                  </button>
                </div>

                {/* Problem Description */}
                <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 leading-relaxed">
                  <span className="font-bold text-slate-900 block mb-0.5">Problem / Requirement:</span>
                  {ticket.helpDescription}
                </div>

                {/* Meta details & Technician Assignment */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                  {ticket.phone && (
                    <div className="flex items-center gap-2 text-slate-700">
                      <Phone className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                      <span>{ticket.phone}</span>
                      <button
                        type="button"
                        onClick={() => handleWhatsAppTechIntro(ticket)}
                        className="text-emerald-600 hover:text-emerald-700 text-[11px] font-semibold flex items-center gap-0.5 ml-auto"
                      >
                        <Send className="w-3 h-3" /> WhatsApp Intro
                      </button>
                    </div>
                  )}

                  {ticket.installationDate && (
                    <div className="flex items-center gap-2 text-indigo-700 font-semibold">
                      <Calendar className="w-3.5 h-3.5 shrink-0" />
                      <span>Scheduled: {ticket.installationDate}</span>
                    </div>
                  )}

                  <div className="flex items-center gap-2">
                    <User className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <span className="text-slate-500 font-medium">Assigned:</span>
                    <select
                      value={ticket.assignedTechnician || ''}
                      onChange={(e) => handleAssignTechnician(ticket, e.target.value)}
                      className="text-xs font-semibold px-2 py-1 border border-slate-300 rounded-lg bg-white outline-none"
                    >
                      <option value="">Unassigned</option>
                      {technicians.map((t) => (
                        <option key={t.username} value={t.username}>
                          {t.displayName}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {/* Workflow Buttons */}
                <div className="pt-3 border-t border-slate-100 flex items-center justify-between flex-wrap gap-2">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    {ticket.status !== 'SOLVED' && (
                      <button
                        onClick={() => handleStatusChange(ticket, 'SOLVED')}
                        className="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 text-xs font-bold rounded-lg flex items-center gap-1.5 transition"
                      >
                        <Check className="w-3.5 h-3.5" />
                        Mark as Solved
                      </button>
                    )}

                    {ticket.status === 'OPEN' && (
                      <button
                        onClick={() => handleStatusChange(ticket, 'ACCEPTED')}
                        className="px-3 py-1.5 bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-bold rounded-lg transition"
                      >
                        Accept Request
                      </button>
                    )}

                    {ticket.status === 'ACCEPTED' && (
                      <button
                        onClick={() => handleStatusChange(ticket, 'INSTALLATION_IN_PROGRESS')}
                        className="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 text-xs font-bold rounded-lg transition"
                      >
                        Start Installation
                      </button>
                    )}

                    {ticket.status === 'INSTALLATION_IN_PROGRESS' && (
                      <button
                        onClick={() => handleStatusChange(ticket, 'INSTALLATION_COMPLETED')}
                        className="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 text-xs font-bold rounded-lg transition"
                      >
                        Installation Done
                      </button>
                    )}
                  </div>

                  {user.isAdmin && (
                    <button
                      onClick={() => handleDelete(ticket.id)}
                      className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                      title="Delete Ticket"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Add Ticket Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200">
            <div className="bg-gradient-to-r from-blue-900 to-indigo-900 text-white p-5 flex justify-between items-center">
              <h3 className="font-bold text-base">New Service or Installation Ticket</h3>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateTicket} className="p-6 space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Ticket Category</label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value as HelpCategory)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-semibold bg-white"
                  >
                    <option value="REGULAR_SERVICE">Regular Service / Maintenance</option>
                    <option value="EMERGENCY">🚨 Customer Need Help (Emergency)</option>
                    <option value="INSTALLATION">New Installation Work</option>
                    <option value="QUOTATION">Quotation Request</option>
                  </select>
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Customer Name *</label>
                  <input
                    type="text"
                    required
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                    placeholder="e.g. Sunil Shetty - Sea Breeze"
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Mobile / WhatsApp</label>
                  <input
                    type="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="e.g. 9422003322"
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Assign Technician</label>
                  <select
                    value={assignedTech}
                    onChange={(e) => setAssignedTech(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none bg-white font-medium"
                  >
                    <option value="">Choose technician...</option>
                    {technicians.map((t) => (
                      <option key={t.username} value={t.username}>
                        {t.displayName}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Installation / Visit Date</label>
                  <input
                    type="date"
                    value={installationDate}
                    onChange={(e) => setInstallationDate(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                  />
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Site Address</label>
                  <textarea
                    rows={2}
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                    placeholder="Site location address in Goa..."
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                  />
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Problem / Requirement Description *</label>
                  <textarea
                    rows={3}
                    required
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Explain the issue (e.g. Camera 3 offline, DVR beep alarm sounding)..."
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-4 py-2 border border-slate-300 text-slate-700 font-semibold rounded-xl hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow-md shadow-blue-600/30"
                >
                  <Check className="w-4 h-4" />
                  Create Ticket
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
