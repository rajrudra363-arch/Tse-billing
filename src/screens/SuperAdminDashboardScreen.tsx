import React from 'react';
import {
  BillEntity,
  CustomerEntity,
  CustomerHelpEntity,
  ProductEntity,
  ExpenseEntity,
  UserAccountEntity,
} from '../types';
import { formatINR } from '../utils/rupeesWords';
import {
  BarChart3,
  TrendingUp,
  AlertTriangle,
  Star,
  Lock,
  Layers,
  LifeBuoy,
} from 'lucide-react';

interface SuperAdminDashboardScreenProps {
  bills: BillEntity[];
  customers: CustomerEntity[];
  helpRequests: CustomerHelpEntity[];
  products: ProductEntity[];
  expenses: ExpenseEntity[];
  accounts: UserAccountEntity[];
}

export const SuperAdminDashboardScreen: React.FC<SuperAdminDashboardScreenProps> = ({
  bills,
  customers,
  helpRequests,
  products,
  expenses,
  accounts,
}) => {
  const taxBills = bills.filter((b) => b.documentType !== 'QUOTATION');
  const quotations = bills.filter((b) => b.documentType === 'QUOTATION');

  const totalRevenue = taxBills.reduce((acc, b) => acc + (b.grandTotal || 0), 0);
  const totalCollected = taxBills.reduce((acc, b) => acc + (b.paidAmount || 0), 0);
  const totalDue = taxBills.reduce((acc, b) => acc + (b.dueAmount || 0), 0);
  const quotationPipeline = quotations.reduce((acc, b) => acc + (b.grandTotal || 0), 0);
  const totalExpenses = expenses.reduce((acc, e) => acc + (e.amount || 0), 0);
  const netProfit = totalCollected - totalExpenses;

  const onlineCollected = taxBills
    .filter((b) => b.paymentMode === 'ONLINE')
    .reduce((acc, b) => acc + (b.paidAmount || 0), 0);
  const cashCollected = taxBills
    .filter((b) => b.paymentMode === 'CASH')
    .reduce((acc, b) => acc + (b.paidAmount || 0), 0);

  const lowStock = products.filter((p) => p.minStockAlert > 0 && p.stockQty <= p.minStockAlert);
  const helpSolved = helpRequests.filter((h) => h.status === 'SOLVED' || h.status === 'RESOLVED').length;
  const helpOpen = helpRequests.filter((h) => h.status === 'OPEN' || h.status === 'ACCEPTED').length;

  const ratedBills = taxBills.filter((b) => b.staffRating > 0);
  const avgRating =
    ratedBills.length > 0
      ? ratedBills.reduce((acc, b) => acc + b.staffRating, 0) / ratedBills.length
      : 5.0;

  // Technicians scorecard
  const technicians = accounts.filter((a) => a.role === 'technician' || a.role === 'staff');
  const techSummaries = technicians.map((t) => {
    const techBills = taxBills.filter(
      (b) => b.createdBy.toLowerCase() === t.username.toLowerCase()
    );
    const techTotalBilled = techBills.reduce((acc, b) => acc + (b.grandTotal || 0), 0);
    const techCollected = techBills.reduce((acc, b) => acc + (b.paidAmount || 0), 0);
    const techRatings = techBills.filter((b) => b.staffRating > 0);
    const techAvgRating =
      techRatings.length > 0
        ? techRatings.reduce((acc, b) => acc + b.staffRating, 0) / techRatings.length
        : 5.0;

    return {
      username: t.username,
      name: t.displayName,
      phone: t.phone,
      billCount: techBills.length,
      billedAmount: techTotalBilled,
      collectedAmount: techCollected,
      avgRating: techAvgRating,
    };
  });

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Banner */}
      <div className="bg-gradient-to-r from-[#1E3A8A] to-[#1E293B] text-white p-5 rounded-2xl shadow-md border border-blue-900 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-white/10 rounded-xl">
            <BarChart3 className="w-6 h-6 text-cyan-300" />
          </div>
          <div>
            <h2 className="text-lg font-black tracking-tight">Business Analytics & Market Intelligence</h2>
            <div className="flex items-center gap-1.5 text-xs text-blue-200 mt-0.5">
              <Lock className="w-3.5 h-3.5 text-amber-300" />
              <span>Read-Only Executive View • Live Collections, Pipelines & Technician Performance</span>
            </div>
          </div>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200">
          <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">Billed Revenue</span>
          <span className="text-xl font-black font-mono text-slate-900 block mt-1">
            {formatINR(totalRevenue)}
          </span>
          <span className="text-[10px] text-slate-400 font-semibold">{taxBills.length} Invoices</span>
        </div>

        <div className="bg-emerald-50/70 p-4 rounded-2xl shadow-sm border border-emerald-200">
          <span className="text-[11px] font-bold text-emerald-800 uppercase tracking-wider block">Net Collections</span>
          <span className="text-xl font-black font-mono text-emerald-700 block mt-1">
            {formatINR(totalCollected)}
          </span>
          <span className="text-[10px] text-emerald-600 font-semibold">Realized Inflows</span>
        </div>

        <div className="bg-rose-50/70 p-4 rounded-2xl shadow-sm border border-rose-200">
          <span className="text-[11px] font-bold text-rose-800 uppercase tracking-wider block">Customer Due</span>
          <span className="text-xl font-black font-mono text-rose-700 block mt-1">
            {formatINR(totalDue)}
          </span>
          <span className="text-[10px] text-rose-600 font-semibold">Receivables</span>
        </div>

        <div className="bg-indigo-50/70 p-4 rounded-2xl shadow-sm border border-indigo-200">
          <span className="text-[11px] font-bold text-indigo-800 uppercase tracking-wider block">Quotes Pipeline</span>
          <span className="text-xl font-black font-mono text-indigo-700 block mt-1">
            {formatINR(quotationPipeline)}
          </span>
          <span className="text-[10px] text-indigo-600 font-semibold">{quotations.length} Active Quotes</span>
        </div>

        <div className="bg-amber-50/70 p-4 rounded-2xl shadow-sm border border-amber-200">
          <span className="text-[11px] font-bold text-amber-800 uppercase tracking-wider block">Total Expenses</span>
          <span className="text-xl font-black font-mono text-amber-700 block mt-1">
            {formatINR(totalExpenses)}
          </span>
          <span className="text-[10px] text-amber-600 font-semibold">{expenses.length} Records</span>
        </div>

        <div className="bg-blue-50/70 p-4 rounded-2xl shadow-sm border border-blue-200">
          <span className="text-[11px] font-bold text-blue-800 uppercase tracking-wider block">Net Operating Profit</span>
          <span className="text-xl font-black font-mono text-blue-900 block mt-1">
            {formatINR(netProfit)}
          </span>
          <span className="text-[10px] text-blue-600 font-semibold">Collections - Expenses</span>
        </div>
      </div>

      {/* Row 2: Inflow Distribution & Help Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {/* Payment Inflow Breakdown */}
        <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-4">
          <h3 className="font-bold text-sm text-slate-800 flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-emerald-600" />
            Payment Channel Split
          </h3>
          <div className="space-y-3">
            <div>
              <div className="flex justify-between text-xs font-semibold mb-1">
                <span className="text-blue-700">Online UPI QR Payment</span>
                <span className="font-mono">{formatINR(onlineCollected)}</span>
              </div>
              <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
                <div
                  className="bg-blue-600 h-2.5 rounded-full"
                  style={{
                    width: `${totalCollected > 0 ? (onlineCollected / totalCollected) * 100 : 50}%`,
                  }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs font-semibold mb-1">
                <span className="text-emerald-700">Cash Settlements</span>
                <span className="font-mono">{formatINR(cashCollected)}</span>
              </div>
              <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
                <div
                  className="bg-emerald-600 h-2.5 rounded-full"
                  style={{
                    width: `${totalCollected > 0 ? (cashCollected / totalCollected) * 100 : 50}%`,
                  }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Service Helpdesk Status */}
        <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-3">
          <h3 className="font-bold text-sm text-slate-800 flex items-center gap-2">
            <LifeBuoy className="w-4 h-4 text-blue-600" />
            Field Support Tickets
          </h3>
          <div className="grid grid-cols-2 gap-3 pt-2">
            <div className="bg-emerald-50 p-3 rounded-xl border border-emerald-200 text-center">
              <span className="text-2xl font-black text-emerald-700 block">{helpSolved}</span>
              <span className="text-[10px] font-bold text-emerald-800 uppercase tracking-wider">
                Solved / Closed
              </span>
            </div>
            <div className="bg-amber-50 p-3 rounded-xl border border-amber-200 text-center">
              <span className="text-2xl font-black text-amber-700 block">{helpOpen}</span>
              <span className="text-[10px] font-bold text-amber-800 uppercase tracking-wider">
                Open / Assigned
              </span>
            </div>
          </div>
          <div className="pt-2 text-xs text-slate-500 flex items-center justify-between">
            <span>Customer Satisfaction Rating:</span>
            <span className="font-bold text-amber-600 flex items-center gap-1">
              <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-500" />
              {avgRating.toFixed(1)} / 5.0
            </span>
          </div>
        </div>

        {/* Low Stock Watchlist */}
        <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-3">
          <h3 className="font-bold text-sm text-slate-800 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500" />
            Low Stock Inventory Alerts ({lowStock.length})
          </h3>
          <div className="max-h-36 overflow-y-auto space-y-2 pr-1">
            {lowStock.length === 0 ? (
              <p className="text-xs text-emerald-600 font-semibold py-4 text-center">
                ✓ All items healthy above min-stock threshold
              </p>
            ) : (
              lowStock.map((p) => (
                <div
                  key={p.id}
                  className="p-2 bg-rose-50 border border-rose-200 rounded-lg text-xs flex justify-between items-center"
                >
                  <span className="font-semibold text-rose-950 truncate max-w-[180px]">{p.name}</span>
                  <span className="font-bold text-rose-700 font-mono">
                    {p.stockQty} {p.unit} (Min: {p.minStockAlert})
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Row 3: Technician Scorecard */}
      <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-4">
        <h3 className="font-bold text-base text-slate-900 flex items-center gap-2">
          <Layers className="w-5 h-5 text-indigo-600" />
          Technician Productivity & Field Collection Scorecard
        </h3>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-100 text-slate-700 font-bold border-b border-slate-200">
              <tr>
                <th className="py-2.5 px-3">Technician</th>
                <th className="py-2.5 px-3 text-center">Bills Created</th>
                <th className="py-2.5 px-3 text-right">Billed Amount</th>
                <th className="py-2.5 px-3 text-right">Collections</th>
                <th className="py-2.5 px-3 text-center">Client Rating</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {techSummaries.map((ts) => (
                <tr key={ts.username} className="hover:bg-slate-50 transition">
                  <td className="py-3 px-3">
                    <span className="font-bold text-slate-900 block">{ts.name}</span>
                    <span className="text-[10px] text-slate-400 font-mono">{ts.username}</span>
                  </td>
                  <td className="py-3 px-3 text-center font-bold text-slate-800">{ts.billCount}</td>
                  <td className="py-3 px-3 text-right font-mono font-bold text-slate-900">
                    {formatINR(ts.billedAmount)}
                  </td>
                  <td className="py-3 px-3 text-right font-mono font-bold text-emerald-700">
                    {formatINR(ts.collectedAmount)}
                  </td>
                  <td className="py-3 px-3 text-center">
                    <span className="inline-flex items-center gap-1 font-bold text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">
                      <Star className="w-3 h-3 fill-amber-400 text-amber-500" />
                      {ts.avgRating.toFixed(1)} ★
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
