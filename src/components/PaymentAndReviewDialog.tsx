import React, { useState } from 'react';
import { BillEntity, PaymentMode } from '../types';
import { formatINR } from '../utils/rupeesWords';
import { Star, X, Check, CreditCard, Banknote } from 'lucide-react';

interface PaymentAndReviewDialogProps {
  bill: BillEntity;
  onDismiss: () => void;
  onSave: (billId: string, paidAmount: number, paymentMode: PaymentMode, rating: number, reviewNote: string) => void;
}

export const PaymentAndReviewDialog: React.FC<PaymentAndReviewDialogProps> = ({
  bill,
  onDismiss,
  onSave,
}) => {
  const [paid, setPaid] = useState<number>(bill.paidAmount);
  const [mode, setMode] = useState<PaymentMode>(bill.paymentMode || 'ONLINE');
  const [rating, setRating] = useState<number>(bill.staffRating || 5);
  const [note, setNote] = useState<string>(bill.staffReviewNote || '');

  const due = Math.max(0, bill.grandTotal - paid);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(bill.id, paid, mode, rating, note);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in duration-200">
        <div className="bg-gradient-to-r from-blue-900 to-indigo-900 text-white p-5 flex justify-between items-center">
          <div>
            <h3 className="font-bold text-lg">Payment Settlement & Staff Review</h3>
            <p className="text-xs text-blue-200">
              {bill.documentType} #{bill.invoiceNo} • {bill.customerName}
            </p>
          </div>
          <button
            onClick={onDismiss}
            className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          <div className="grid grid-cols-2 gap-3 bg-slate-50 p-3 rounded-xl border border-slate-200 text-sm">
            <div>
              <span className="text-xs text-slate-500 font-medium">Grand Total</span>
              <p className="font-bold text-slate-900 text-base font-mono">{formatINR(bill.grandTotal)}</p>
            </div>
            <div>
              <span className="text-xs text-slate-500 font-medium">Remaining Due</span>
              <p className={`font-bold text-base font-mono ${due > 0 ? 'text-rose-600' : 'text-emerald-600'}`}>
                {formatINR(due)}
              </p>
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Paid Amount (₹)
            </label>
            <div className="flex gap-2">
              <input
                type="number"
                step="any"
                min="0"
                max={bill.grandTotal}
                value={paid}
                onChange={(e) => setPaid(parseFloat(e.target.value) || 0)}
                className="flex-1 px-4 py-2.5 border border-slate-300 rounded-xl font-bold font-mono text-base focus:ring-2 focus:ring-blue-500 outline-none"
              />
              <button
                type="button"
                onClick={() => setPaid(bill.grandTotal)}
                className="px-3 py-2 bg-emerald-100 text-emerald-800 text-xs font-bold rounded-xl hover:bg-emerald-200 transition"
              >
                Mark Paid in Full
              </button>
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Payment Mode
            </label>
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setMode('ONLINE')}
                className={`flex items-center justify-center gap-2 p-3 rounded-xl border font-bold text-sm transition ${
                  mode === 'ONLINE'
                    ? 'border-blue-600 bg-blue-50 text-blue-800 ring-2 ring-blue-500/20'
                    : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                }`}
              >
                <CreditCard className="w-4 h-4 text-blue-600" />
                Online / UPI QR
              </button>
              <button
                type="button"
                onClick={() => setMode('CASH')}
                className={`flex items-center justify-center gap-2 p-3 rounded-xl border font-bold text-sm transition ${
                  mode === 'CASH'
                    ? 'border-emerald-600 bg-emerald-50 text-emerald-800 ring-2 ring-emerald-500/20'
                    : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                }`}
              >
                <Banknote className="w-4 h-4 text-emerald-600" />
                Cash
              </button>
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Technician / Service Rating
            </label>
            <div className="flex items-center gap-2">
              {[1, 2, 3, 4, 5].map((star) => (
                <button
                  type="button"
                  key={star}
                  onClick={() => setRating(star)}
                  className="p-1 hover:scale-110 transition focus:outline-none"
                >
                  <Star
                    className={`w-7 h-7 ${
                      star <= rating ? 'fill-amber-400 text-amber-500' : 'text-slate-300'
                    }`}
                  />
                </button>
              ))}
              <span className="text-sm font-bold text-slate-600 ml-2">
                {rating === 5 ? '5.0 ★ Excellent' : `${rating}.0 ★`}
              </span>
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Customer Feedback & Service Notes
            </label>
            <textarea
              rows={2}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="e.g. Work verified, all 4 channels online on mobile app..."
              className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-blue-500 outline-none"
            />
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-200">
            <button
              type="button"
              onClick={onDismiss}
              className="px-5 py-2.5 rounded-xl border border-slate-300 font-semibold text-slate-700 hover:bg-slate-100 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-6 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold flex items-center gap-2 shadow-lg shadow-blue-600/30 transition"
            >
              <Check className="w-4 h-4" />
              Save Settlement
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
