import React, { useState, useEffect } from 'react';
import { BillEntity, CompanySettings, UpiSettings } from '../types';
import { formatINR } from '../utils/rupeesWords';
import { Printer, Download, Share2, MessageCircle, QrCode, CheckCircle2, X } from 'lucide-react';
import { buildUpiUri, generateQrDataUrl } from '../utils/qrGenerator';

interface ExportOptionsModalProps {
  bill: BillEntity;
  company: CompanySettings;
  upi: UpiSettings;
  onDismiss: () => void;
  onPrintA4: () => void;
  onOpenPaymentReview: () => void;
}

export const ExportOptionsModal: React.FC<ExportOptionsModalProps> = ({
  bill,
  company,
  upi,
  onDismiss,
  onPrintA4,
  onOpenPaymentReview,
}) => {
  const [dueQrUrl, setDueQrUrl] = useState<string>('');

  useEffect(() => {
    if (bill.dueAmount > 0.009 && upi.upiId) {
      const upiUri = buildUpiUri(upi.upiId, upi.payeeName, bill.dueAmount, bill.invoiceNo);
      generateQrDataUrl(upiUri, 280).then(setDueQrUrl);
    }
  }, [bill.dueAmount, bill.invoiceNo, upi.upiId, upi.payeeName]);

  const handleWhatsAppShare = () => {
    const rawPhone = bill.customerDetails.match(/(\+?91[\s-]?)?[6-9]\d{9}/);
    const phone = rawPhone ? rawPhone[0].replace(/\D/g, '') : '';
    const formattedPhone = phone.length === 10 ? `91${phone}` : phone;

    const message = `*${company.companyName}*\n${bill.documentType === 'QUOTATION' ? 'Quotation' : 'Invoice'} No: *${bill.invoiceNo}*\nCustomer: *${bill.customerName || 'Customer'}*\nSub Total: ${formatINR(bill.subtotal)}\nDiscount: ${formatINR(bill.discount)}\n*Grand Total: ${formatINR(bill.grandTotal)}*\n${bill.documentType !== 'QUOTATION' ? `Paid Amount: ${formatINR(bill.paidAmount)}\n*Due Balance: ${formatINR(bill.dueAmount)}*\n` : ''}Thank you for choosing ${company.companyName}!`;

    const url = formattedPhone
      ? `https://wa.me/${formattedPhone}?text=${encodeURIComponent(message)}`
      : `https://wa.me/?text=${encodeURIComponent(message)}`;

    window.open(url, '_blank');
  };

  const handleDueQrWhatsAppShare = () => {
    const rawPhone = bill.customerDetails.match(/(\+?91[\s-]?)?[6-9]\d{9}/);
    const phone = rawPhone ? rawPhone[0].replace(/\D/g, '') : '';
    const formattedPhone = phone.length === 10 ? `91${phone}` : phone;

    const message = `*${company.companyName} — Due Payment Request*\nInvoice No: *${bill.invoiceNo}*\nCustomer: ${bill.customerName}\n*Due Amount: ${formatINR(bill.dueAmount)}*\nUPI ID: ${upi.upiId}\nPayee: ${upi.payeeName}\nScan UPI QR or pay exact due balance to settle your invoice. Thank you!`;

    const url = formattedPhone
      ? `https://wa.me/${formattedPhone}?text=${encodeURIComponent(message)}`
      : `https://wa.me/?text=${encodeURIComponent(message)}`;

    window.open(url, '_blank');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in duration-200">
        <div className="bg-gradient-to-r from-emerald-600 to-teal-700 text-white p-5 flex justify-between items-center">
          <div className="flex items-center gap-3">
            <CheckCircle2 className="w-8 h-8 text-emerald-200" />
            <div>
              <h3 className="font-bold text-lg">Document Ready!</h3>
              <p className="text-xs text-emerald-100">
                {bill.documentType === 'QUOTATION' ? 'Quotation' : 'Invoice'} #{bill.invoiceNo}
              </p>
            </div>
          </div>
          <button
            onClick={onDismiss}
            className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 text-center">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block">
              Customer: {bill.customerName || 'Walk-in'}
            </span>
            <span className="text-2xl font-black text-slate-900 font-mono block my-1">
              {formatINR(bill.grandTotal)}
            </span>
            <div className="flex justify-center gap-2 text-xs font-semibold">
              <span className="text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded-full">
                Paid: {formatINR(bill.paidAmount)}
              </span>
              {bill.dueAmount > 0 && (
                <span className="text-rose-700 bg-rose-100 px-2 py-0.5 rounded-full">
                  Due: {formatINR(bill.dueAmount)}
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-2.5">
            <button
              onClick={() => {
                onPrintA4();
                onDismiss();
              }}
              className="flex items-center justify-between p-3.5 rounded-xl border-2 border-blue-600 bg-blue-50/50 hover:bg-blue-100/50 text-blue-900 font-bold transition group"
            >
              <div className="flex items-center gap-3">
                <Printer className="w-5 h-5 text-blue-600" />
                <span>Print Official A4 Sheet / PDF</span>
              </div>
              <Download className="w-4 h-4 text-blue-400 group-hover:translate-y-0.5 transition" />
            </button>

            <button
              onClick={handleWhatsAppShare}
              className="flex items-center justify-between p-3.5 rounded-xl border border-emerald-600 bg-emerald-50 hover:bg-emerald-100 text-emerald-950 font-bold transition group"
            >
              <div className="flex items-center gap-3">
                <MessageCircle className="w-5 h-5 text-emerald-600" />
                <span>Share Bill on WhatsApp</span>
              </div>
              <Share2 className="w-4 h-4 text-emerald-500" />
            </button>

            {bill.dueAmount > 0 && (
              <button
                onClick={handleDueQrWhatsAppShare}
                className="flex items-center justify-between p-3.5 rounded-xl border border-rose-300 bg-rose-50 hover:bg-rose-100 text-rose-950 font-bold transition group"
              >
                <div className="flex items-center gap-3">
                  <QrCode className="w-5 h-5 text-rose-600" />
                  <span>Send Due Balance QR on WhatsApp</span>
                </div>
                <Share2 className="w-4 h-4 text-rose-400" />
              </button>
            )}

            <button
              onClick={() => {
                onOpenPaymentReview();
                onDismiss();
              }}
              className="flex items-center justify-center p-3 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-100 font-semibold text-sm transition"
            >
              Update Payment & Staff Review
            </button>
          </div>

          {dueQrUrl && (
            <div className="mt-3 p-3 bg-slate-50 border border-slate-200 rounded-xl flex items-center gap-3">
              <img src={dueQrUrl} alt="Due QR" className="w-16 h-16 rounded border bg-white" />
              <div>
                <span className="text-xs font-bold text-rose-700 block">Due Amount QR Card</span>
                <span className="text-[11px] text-slate-600 block">
                  Customer can scan to pay {formatINR(bill.dueAmount)} directly.
                </span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
