import React, { useState } from 'react';
import { BillEntity, CompanySettings, UpiSettings, BillDraft } from '../types';
import { formatINR } from '../utils/rupeesWords';
import { sound } from '../utils/soundEngine';
import { AppDb } from '../storage/db';
import { InvoiceSheetPreview } from '../components/InvoiceSheetPreview';
import { PaymentAndReviewDialog } from '../components/PaymentAndReviewDialog';
import {
  Search,
  Filter,
  Printer,
  Share2,
  Trash2,
  Edit,
  Eye,
  CheckCircle2,
  Clock,
  AlertCircle,
  FileText,
  DollarSign,
  X,
  CreditCard,
} from 'lucide-react';

interface SavedBillsScreenProps {
  bills: BillEntity[];
  company: CompanySettings;
  upi: UpiSettings;
  isAdmin: boolean;
  onEditBill: (bill: BillEntity) => void;
  onBillsUpdated: () => void;
}

export const SavedBillsScreen: React.FC<SavedBillsScreenProps> = ({
  bills,
  company,
  upi,
  isAdmin,
  onEditBill,
  onBillsUpdated,
}) => {
  const [search, setSearch] = useState<string>('');
  const [filter, setFilter] = useState<'ALL' | 'PAID' | 'PARTIAL' | 'UNPAID' | 'QUOTATION'>('ALL');
  const [sheetPreviewBill, setSheetPreviewBill] = useState<BillEntity | null>(null);
  const [settlementBill, setSettlementBill] = useState<BillEntity | null>(null);

  // Filter bills
  const filtered = bills.filter((b) => {
    const matchesSearch =
      b.invoiceNo.toLowerCase().includes(search.toLowerCase()) ||
      b.customerName.toLowerCase().includes(search.toLowerCase()) ||
      b.customerDetails.toLowerCase().includes(search.toLowerCase()) ||
      b.itemsJson.toLowerCase().includes(search.toLowerCase());

    if (!matchesSearch) return false;

    if (filter === 'ALL') return true;
    if (filter === 'QUOTATION') return b.documentType === 'QUOTATION';
    if (b.documentType === 'QUOTATION') return false;

    if (filter === 'PAID') return b.paymentStatus === 'PAID';
    if (filter === 'PARTIAL') return b.paymentStatus === 'PARTIAL';
    if (filter === 'UNPAID') return b.paymentStatus === 'UNPAID';

    return true;
  });

  // Calculate KPIs
  const taxBills = bills.filter((b) => b.documentType !== 'QUOTATION');
  const totalBilled = taxBills.reduce((acc, b) => acc + (b.grandTotal || 0), 0);
  const totalCollected = taxBills.reduce((acc, b) => acc + (b.paidAmount || 0), 0);
  const totalDue = taxBills.reduce((acc, b) => acc + (b.dueAmount || 0), 0);

  const handleDelete = (bill: BillEntity) => {
    if (confirm(`Move ${bill.documentType} #${bill.invoiceNo} to Trash?`)) {
      AppDb.deleteBill(bill.id);
      sound.playAlert();
      onBillsUpdated();
    }
  };

  const handleMarkDuePaid = (bill: BillEntity) => {
    const updated: BillEntity = {
      ...bill,
      paidAmount: bill.grandTotal,
      dueAmount: 0,
      paymentStatus: 'PAID',
      updatedAt: Date.now(),
    };
    AppDb.saveBill(updated);
    sound.playSuccess();
    onBillsUpdated();
  };

  const handleWhatsAppShare = (bill: BillEntity) => {
    const rawPhone = bill.customerDetails.match(/(\+?91[\s-]?)?[6-9]\d{9}/);
    const phone = rawPhone ? rawPhone[0].replace(/\D/g, '') : '';
    const formattedPhone = phone.length === 10 ? `91${phone}` : phone;

    const message = `*${company.companyName}*\n${bill.documentType === 'QUOTATION' ? 'Quotation' : 'Invoice'} No: *${bill.invoiceNo}*\nCustomer: *${bill.customerName || 'Customer'}*\nSub Total: ${formatINR(bill.subtotal)}\nDiscount: ${formatINR(bill.discount)}\n*Grand Total: ${formatINR(bill.grandTotal)}*\n${bill.documentType !== 'QUOTATION' ? `Paid Amount: ${formatINR(bill.paidAmount)}\n*Due Balance: ${formatINR(bill.dueAmount)}*\n` : ''}Thank you for choosing ${company.companyName}!`;

    const url = formattedPhone
      ? `https://wa.me/${formattedPhone}?text=${encodeURIComponent(message)}`
      : `https://wa.me/?text=${encodeURIComponent(message)}`;

    window.open(url, '_blank');
  };

  // Convert BillEntity to BillDraft for Preview
  const toDraft = (b: BillEntity): BillDraft => {
    let items = [];
    try {
      items = JSON.parse(b.itemsJson || '[]');
    } catch {
      items = [];
    }
    return {
      documentType: b.documentType,
      invoiceNo: b.invoiceNo,
      date: b.date,
      customerName: b.customerName,
      customerDetails: b.customerDetails,
      customerLocationUrl: '',
      items,
      subtotal: b.subtotal,
      discount: b.discount,
      extraChargeType: b.extraChargeType,
      extraChargeAmount: b.extraChargeAmount,
      secondaryChargeType: b.secondaryChargeType,
      secondaryChargeAmount: b.secondaryChargeAmount,
      grandTotal: b.grandTotal,
      paidAmount: b.paidAmount,
      dueAmount: b.dueAmount,
      paymentMode: b.paymentMode,
      signatureBase64: b.signatureBase64,
      customQrText: b.customQrText,
      staffRating: b.staffRating,
      staffReviewNote: b.staffReviewNote,
      quotationNote: b.quotationNote,
      isHelpBill: b.isHelpBill,
      helpRequestId: b.helpRequestId,
    };
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header & KPI Summary */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
        <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200">
          <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">Total Billed</span>
          <span className="text-xl sm:text-2xl font-black font-mono text-slate-900 block mt-1">
            {formatINR(totalBilled)}
          </span>
          <span className="text-[10px] text-slate-400 font-semibold">{taxBills.length} Invoices</span>
        </div>

        <div className="bg-emerald-50/60 p-4 rounded-2xl shadow-sm border border-emerald-200">
          <span className="text-[11px] font-bold text-emerald-800 uppercase tracking-wider block">Collected</span>
          <span className="text-xl sm:text-2xl font-black font-mono text-emerald-700 block mt-1">
            {formatINR(totalCollected)}
          </span>
          <span className="text-[10px] text-emerald-600 font-semibold">Cash & Online UPI</span>
        </div>

        <div className="bg-rose-50/60 p-4 rounded-2xl shadow-sm border border-rose-200">
          <span className="text-[11px] font-bold text-rose-800 uppercase tracking-wider block">Customer Due</span>
          <span className="text-xl sm:text-2xl font-black font-mono text-rose-700 block mt-1">
            {formatINR(totalDue)}
          </span>
          <span className="text-[10px] text-rose-600 font-semibold">Pending Collections</span>
        </div>

        <div className="bg-indigo-50/60 p-4 rounded-2xl shadow-sm border border-indigo-200">
          <span className="text-[11px] font-bold text-indigo-800 uppercase tracking-wider block">Total Docs</span>
          <span className="text-xl sm:text-2xl font-black font-mono text-indigo-700 block mt-1">
            {bills.length}
          </span>
          <span className="text-[10px] text-indigo-600 font-semibold">
            {bills.filter((b) => b.documentType === 'QUOTATION').length} Quotations
          </span>
        </div>
      </div>

      {/* Controls: Search & Filter Chips */}
      <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200 flex flex-col sm:flex-row gap-3 justify-between items-stretch sm:items-center">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
          <input
            type="text"
            placeholder="Search invoice #, customer name, phone, item..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2 text-xs border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
          />
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
          <Filter className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          {(['ALL', 'PAID', 'PARTIAL', 'UNPAID', 'QUOTATION'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => {
                sound.playClick();
                setFilter(tab);
              }}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition ${
                filter === tab
                  ? 'bg-blue-600 text-white shadow'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {tab === 'ALL' ? 'All Docs' : tab}
            </button>
          ))}
        </div>
      </div>

      {/* Bills Cards List */}
      <div className="space-y-3">
        {filtered.length === 0 ? (
          <div className="p-12 text-center bg-white rounded-2xl border border-slate-200 text-slate-400">
            <FileText className="w-10 h-10 mx-auto text-slate-300 mb-2" />
            <p className="font-semibold text-sm">No invoices or quotations match your search.</p>
          </div>
        ) : (
          filtered.map((bill) => {
            const isQuotation = bill.documentType === 'QUOTATION';
            return (
              <div
                key={bill.id}
                className="bg-white p-4 sm:p-5 rounded-2xl shadow-sm border border-slate-200 hover:border-blue-400 transition space-y-3"
              >
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-black text-sm text-slate-900 font-mono">
                        {bill.invoiceNo}
                      </span>
                      <span
                        className={`px-2 py-0.5 text-[10px] font-extrabold uppercase rounded-full ${
                          isQuotation
                            ? 'bg-indigo-100 text-indigo-800'
                            : 'bg-blue-100 text-blue-800'
                        }`}
                      >
                        {isQuotation ? 'Quotation' : 'Tax Invoice'}
                      </span>
                      {!isQuotation && (
                        <span
                          className={`px-2 py-0.5 text-[10px] font-extrabold uppercase rounded-full ${
                            bill.paymentStatus === 'PAID'
                              ? 'bg-emerald-100 text-emerald-800'
                              : bill.paymentStatus === 'PARTIAL'
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-rose-100 text-rose-800'
                          }`}
                        >
                          {bill.paymentStatus}
                        </span>
                      )}
                      <span className="text-xs text-slate-400 font-medium">{bill.date}</span>
                    </div>
                    <div className="font-bold text-sm text-slate-800">
                      {bill.customerName || 'Walk-in Customer'}
                    </div>
                    {bill.customerDetails && (
                      <p className="text-[11px] text-slate-500 whitespace-pre-line line-clamp-1">
                        {bill.customerDetails}
                      </p>
                    )}
                  </div>

                  {/* Financials & Mode */}
                  <div className="text-left sm:text-right bg-slate-50 sm:bg-transparent p-2.5 sm:p-0 rounded-xl w-full sm:w-auto">
                    <span className="text-base font-black font-mono text-slate-900 block">
                      {formatINR(bill.grandTotal)}
                    </span>
                    {!isQuotation && (
                      <div className="text-xs font-semibold flex sm:justify-end gap-2 mt-0.5">
                        <span className="text-emerald-700">Paid: {formatINR(bill.paidAmount)}</span>
                        {bill.dueAmount > 0 && (
                          <span className="text-rose-600 font-bold">Due: {formatINR(bill.dueAmount)}</span>
                        )}
                      </div>
                    )}
                    <span className="text-[10px] text-slate-400 font-medium capitalize">
                      By: {bill.createdBy} • Mode: {bill.paymentMode}
                    </span>
                  </div>
                </div>

                {/* Actions Row */}
                <div className="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <button
                      onClick={() => setSheetPreviewBill(bill)}
                      className="px-3 py-1.5 bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-bold rounded-lg flex items-center gap-1.5 transition"
                    >
                      <Eye className="w-3.5 h-3.5" />
                      View Sheet
                    </button>

                    <button
                      onClick={() => {
                        setSheetPreviewBill(bill);
                        setTimeout(() => window.print(), 200);
                      }}
                      className="px-3 py-1.5 border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
                    >
                      <Printer className="w-3.5 h-3.5" />
                      Print A4
                    </button>

                    <button
                      onClick={() => handleWhatsAppShare(bill)}
                      className="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 text-xs font-bold rounded-lg flex items-center gap-1.5 transition"
                    >
                      <Share2 className="w-3.5 h-3.5 text-emerald-600" />
                      WhatsApp
                    </button>

                    {!isQuotation && bill.dueAmount > 0 && (
                      <button
                        onClick={() => handleMarkDuePaid(bill)}
                        className="px-3 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 text-xs font-bold rounded-lg flex items-center gap-1.5 transition"
                      >
                        <CheckCircle2 className="w-3.5 h-3.5 text-rose-600" />
                        Mark Due Paid
                      </button>
                    )}

                    {!isQuotation && (
                      <button
                        onClick={() => setSettlementBill(bill)}
                        className="px-3 py-1.5 border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
                      >
                        <CreditCard className="w-3.5 h-3.5 text-slate-500" />
                        Settlement / Review
                      </button>
                    )}
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => onEditBill(bill)}
                      className="p-1.5 text-slate-400 hover:text-blue-600 transition"
                      title="Load into Edit Draft"
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    {isAdmin && (
                      <button
                        onClick={() => handleDelete(bill)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                        title="Delete to Trash"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* A4 Sheet Modal */}
      {sheetPreviewBill && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-4xl w-full p-4 overflow-y-auto max-h-[92vh] space-y-4">
            <div className="no-print flex justify-between items-center border-b pb-3">
              <span className="font-bold text-sm text-slate-800">
                True A4 Sheet: {sheetPreviewBill.documentType} #{sheetPreviewBill.invoiceNo}
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => window.print()}
                  className="px-3 py-1.5 bg-blue-600 text-white font-bold text-xs rounded-lg hover:bg-blue-700 transition"
                >
                  Print / Save PDF
                </button>
                <button
                  onClick={() => setSheetPreviewBill(null)}
                  className="p-1.5 rounded-lg border text-slate-600 hover:bg-slate-100 transition"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>
            <InvoiceSheetPreview
              draft={toDraft(sheetPreviewBill)}
              company={company}
              upi={upi}
            />
          </div>
        </div>
      )}

      {/* Settlement Review Modal */}
      {settlementBill && (
        <PaymentAndReviewDialog
          bill={settlementBill}
          onDismiss={() => setSettlementBill(null)}
          onSave={(billId, paid, mode, rating, note) => {
            const updatedBill = {
              ...settlementBill,
              paidAmount: paid,
              dueAmount: Math.max(0, settlementBill.grandTotal - paid),
              paymentMode: mode,
              staffRating: rating,
              staffReviewNote: note,
              paymentStatus: (paid >= settlementBill.grandTotal - 0.01 ? 'PAID' : paid > 0 ? 'PARTIAL' : 'UNPAID') as any,
              updatedAt: Date.now(),
            };
            AppDb.saveBill(updatedBill);
            setSettlementBill(null);
            sound.playSuccess();
            onBillsUpdated();
          }}
        />
      )}
    </div>
  );
};
