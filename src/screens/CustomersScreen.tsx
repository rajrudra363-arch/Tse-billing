import React, { useState } from 'react';
import { CustomerEntity, BillEntity, UserSession } from '../types';
import { AppDb } from '../storage/db';
import { formatINR } from '../utils/rupeesWords';
import { sound } from '../utils/soundEngine';
import {
  Users,
  Search,
  Plus,
  Phone,
  MapPin,
  FileText,
  Trash2,
  Edit,
  ExternalLink,
  MessageCircle,
  X,
  Check,
} from 'lucide-react';

interface CustomersScreenProps {
  customers: CustomerEntity[];
  bills: BillEntity[];
  user: UserSession;
  onCustomersUpdated: () => void;
  onOpenBillInDraft: (bill: BillEntity) => void;
}

export const CustomersScreen: React.FC<CustomersScreenProps> = ({
  customers,
  bills,
  user,
  onCustomersUpdated,
  onOpenBillInDraft,
}) => {
  const [search, setSearch] = useState('');
  const [modalCustomer, setModalCustomer] = useState<CustomerEntity | null>(null);
  const [isAddMode, setIsAddMode] = useState(false);
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerEntity | null>(null);

  // Form State
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [locationUrl, setLocationUrl] = useState('');

  const filtered = customers.filter(
    (c) =>
      c.name.toLowerCase().includes(search.toLowerCase()) ||
      c.phone.includes(search) ||
      c.address.toLowerCase().includes(search.toLowerCase())
  );

  const openAddModal = () => {
    sound.playClick();
    setName('');
    setPhone('');
    setAddress('');
    setLocationUrl('');
    setIsAddMode(true);
    setModalCustomer(null);
  };

  const openEditModal = (c: CustomerEntity) => {
    sound.playClick();
    setName(c.name);
    setPhone(c.phone);
    setAddress(c.address);
    setLocationUrl(c.locationUrl);
    setModalCustomer(c);
    setIsAddMode(false);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const payload: CustomerEntity = {
      id: modalCustomer ? modalCustomer.id : 0,
      name: name.trim(),
      phone: phone.trim(),
      address: address.trim(),
      locationUrl: locationUrl.trim(),
      details: `Phone: ${phone.trim()}\n${address.trim()}`,
    };

    AppDb.saveCustomer(payload);
    sound.playSuccess();
    setModalCustomer(null);
    setIsAddMode(false);
    onCustomersUpdated();
  };

  const handleDelete = (c: CustomerEntity) => {
    if (confirm(`Delete customer "${c.name}"?`)) {
      AppDb.deleteCustomer(c.id, user.username);
      sound.playAlert();
      onCustomersUpdated();
      if (selectedCustomer?.id === c.id) setSelectedCustomer(null);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header */}
      <div className="bg-white p-4 sm:p-5 rounded-2xl shadow-sm border border-slate-200 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-2">
            <Users className="w-5 h-5 text-blue-600" />
            Customer Directory ({customers.length})
          </h2>
          <p className="text-xs text-slate-500">
            Manage client profiles, contact numbers, installation site addresses and GPS coordinates
          </p>
        </div>

        <button
          onClick={openAddModal}
          className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/30 flex items-center gap-1.5 transition"
        >
          <Plus className="w-4 h-4" />
          Add Customer
        </button>
      </div>

      {/* Search */}
      <div className="bg-white p-3 rounded-2xl shadow-sm border border-slate-200">
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
          <input
            type="text"
            placeholder="Search by customer name, phone number, village / city..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2 text-xs border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
          />
        </div>
      </div>

      {/* Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filtered.map((c) => {
          const customerBills = bills.filter(
            (b) => b.customerName.toLowerCase().trim() === c.name.toLowerCase().trim()
          );
          const totalSpent = customerBills.reduce((acc, b) => acc + (b.grandTotal || 0), 0);
          const totalDue = customerBills.reduce((acc, b) => acc + (b.dueAmount || 0), 0);

          return (
            <div
              key={c.id}
              className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 hover:border-blue-400 transition space-y-3.5 flex flex-col justify-between"
            >
              <div className="space-y-2">
                <div className="flex justify-between items-start gap-2">
                  <h3 className="font-bold text-slate-900 text-base">{c.name}</h3>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => openEditModal(c)}
                      className="p-1.5 text-slate-400 hover:text-blue-600 transition"
                      title="Edit Customer"
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    {user.isAdmin && (
                      <button
                        onClick={() => handleDelete(c)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                        title="Delete Customer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>

                {c.phone && (
                  <div className="flex items-center gap-2 text-xs font-semibold text-slate-700">
                    <Phone className="w-3.5 h-3.5 text-emerald-600" />
                    <span>{c.phone}</span>
                    <a
                      href={`https://wa.me/91${c.phone.replace(/\D/g, '')}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-emerald-600 hover:text-emerald-700 text-[11px] ml-auto flex items-center gap-1"
                    >
                      <MessageCircle className="w-3 h-3" /> WhatsApp
                    </a>
                  </div>
                )}

                {c.address && (
                  <div className="flex items-start gap-2 text-xs text-slate-600">
                    <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
                    <span className="line-clamp-2">{c.address}</span>
                  </div>
                )}

                {c.locationUrl && (
                  <a
                    href={c.locationUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 hover:underline"
                  >
                    <ExternalLink className="w-3 h-3" /> View Site on Google Maps
                  </a>
                )}
              </div>

              {/* Purchase Summary */}
              <div className="pt-3 border-t border-slate-100 flex justify-between items-center text-xs">
                <div>
                  <span className="text-[10px] text-slate-400 font-semibold block">Total Spent</span>
                  <span className="font-black font-mono text-slate-800">{formatINR(totalSpent)}</span>
                </div>
                {totalDue > 0 ? (
                  <div className="text-right">
                    <span className="text-[10px] text-rose-500 font-semibold block">Pending Due</span>
                    <span className="font-black font-mono text-rose-600">{formatINR(totalDue)}</span>
                  </div>
                ) : (
                  <div className="text-right">
                    <span className="text-[10px] text-slate-400 font-semibold block">Invoices</span>
                    <span className="font-bold text-slate-700">{customerBills.length} Bills</span>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Add / Edit Modal */}
      {(isAddMode || modalCustomer) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-slate-200">
            <div className="bg-gradient-to-r from-blue-900 to-indigo-900 text-white p-5 flex justify-between items-center">
              <h3 className="font-bold text-base">
                {isAddMode ? 'Add New Customer' : 'Edit Customer Profile'}
              </h3>
              <button
                onClick={() => {
                  setIsAddMode(false);
                  setModalCustomer(null);
                }}
                className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSave} className="p-6 space-y-4 text-xs">
              <div>
                <label className="block font-bold text-slate-700 mb-1">Customer / Villa Name *</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Hotel Cidade Vista"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                />
              </div>

              <div>
                <label className="block font-bold text-slate-700 mb-1">Mobile / WhatsApp Number</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="e.g. 9822114455"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                />
              </div>

              <div>
                <label className="block font-bold text-slate-700 mb-1">Installation Site Address</label>
                <textarea
                  rows={2}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="e.g. Naika Vaddo, Calangute, North Goa 403516"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                />
              </div>

              <div>
                <label className="block font-bold text-slate-700 mb-1">Google Maps Location Link</label>
                <input
                  type="url"
                  value={locationUrl}
                  onChange={(e) => setLocationUrl(e.target.value)}
                  placeholder="https://maps.google.com/..."
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => {
                    setIsAddMode(false);
                    setModalCustomer(null);
                  }}
                  className="px-4 py-2 border border-slate-300 text-slate-700 font-semibold rounded-xl hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow-md shadow-blue-600/30"
                >
                  <Check className="w-4 h-4" />
                  Save Customer
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
