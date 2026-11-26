import React, { useState } from 'react';
import { EmployeeEntity, SalaryPaymentEntity, UserSession } from '../types';
import { AppDb } from '../storage/db';
import { calculateEmployeeSummary, isDuplicatePayment, todayIso } from '../utils/salaryCalculator';
import { formatINR } from '../utils/rupeesWords';
import { sound } from '../utils/soundEngine';
import {
  Users,
  Plus,
  DollarSign,
  Calendar,
  CheckCircle,
  AlertCircle,
  Phone,
  FileText,
  Clock,
  Trash2,
  X,
  Check,
} from 'lucide-react';

interface EmployeeManagementScreenProps {
  employees: EmployeeEntity[];
  user: UserSession;
  onEmployeesUpdated: () => void;
}

export const EmployeeManagementScreen: React.FC<EmployeeManagementScreenProps> = ({
  employees,
  user,
  onEmployeesUpdated,
}) => {
  const allPayments = AppDb.getSalaryPayments();

  // Employee Modal State
  const [employeeModalOpen, setEmployeeModalOpen] = useState(false);
  const [editingEmp, setEditingEmp] = useState<EmployeeEntity | null>(null);
  const [empCode, setEmpCode] = useState('');
  const [empName, setEmpName] = useState('');
  const [empMobile, setEmpMobile] = useState('');
  const [hiringDate, setHiringDate] = useState(todayIso());
  const [workStartDate, setWorkStartDate] = useState(todayIso());
  const [workStopDate, setWorkStopDate] = useState('');
  const [monthlySalary, setMonthlySalary] = useState<number>(25000);
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [isActive, setIsActive] = useState(true);

  // Record Payment Modal State
  const [paymentModalEmp, setPaymentModalEmp] = useState<EmployeeEntity | null>(null);
  const [paymentAmount, setPaymentAmount] = useState<number>(0);
  const [paymentDate, setPaymentDate] = useState(todayIso());
  const [paymentMethod, setPaymentMethod] = useState('Bank Transfer');
  const [referenceNo, setReferenceNo] = useState('');
  const [paymentNotes, setPaymentNotes] = useState('');
  const [paymentError, setPaymentError] = useState('');

  // Salary Slip Modal
  const [slipModalEmp, setSlipModalEmp] = useState<EmployeeEntity | null>(null);

  const openAddEmployee = () => {
    sound.playClick();
    setEditingEmp(null);
    setEmpCode(`EMP-${Math.floor(100 + employees.length + 1)}`);
    setEmpName('');
    setEmpMobile('');
    setHiringDate(todayIso());
    setWorkStartDate(todayIso());
    setWorkStopDate('');
    setMonthlySalary(25000);
    setAddress('');
    setNotes('');
    setIsActive(true);
    setEmployeeModalOpen(true);
  };

  const openEditEmployee = (emp: EmployeeEntity) => {
    sound.playClick();
    setEditingEmp(emp);
    setEmpCode(emp.employeeCode);
    setEmpName(emp.name);
    setEmpMobile(emp.mobile);
    setHiringDate(emp.hiringDate);
    setWorkStartDate(emp.workStartDate);
    setWorkStopDate(emp.workStopDate || '');
    setMonthlySalary(emp.monthlySalary);
    setAddress(emp.address || '');
    setNotes(emp.notes || '');
    setIsActive(emp.isActive);
    setEmployeeModalOpen(true);
  };

  const handleSaveEmployee = (e: React.FormEvent) => {
    e.preventDefault();
    if (!empName.trim()) return;

    const payload: EmployeeEntity = {
      employeeId: editingEmp ? editingEmp.employeeId : `emp_${Date.now()}`,
      employeeCode: empCode.trim() || `EMP-${Date.now()}`,
      companyId: 'total_solution',
      name: empName.trim(),
      mobile: empMobile.trim(),
      hiringDate,
      workStartDate,
      workStopDate,
      monthlySalary,
      address: address.trim(),
      notes: notes.trim(),
      isActive,
      createdAt: editingEmp ? editingEmp.createdAt : Date.now(),
      updatedAt: Date.now(),
      createdByUid: user.username,
    };

    AppDb.saveEmployee(payload);
    sound.playSuccess();
    setEmployeeModalOpen(false);
    onEmployeesUpdated();
  };

  const handleDeleteEmployee = (id: string) => {
    if (confirm('Delete this employee record?')) {
      AppDb.deleteEmployee(id);
      sound.playAlert();
      onEmployeesUpdated();
    }
  };

  const openRecordPayment = (emp: EmployeeEntity, remainingBalance: number) => {
    sound.playClick();
    setPaymentModalEmp(emp);
    setPaymentAmount(remainingBalance > 0 ? remainingBalance : emp.monthlySalary);
    setPaymentDate(todayIso());
    setPaymentMethod('Bank Transfer');
    setReferenceNo('');
    setPaymentNotes('');
    setPaymentError('');
  };

  const handleSavePayment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!paymentModalEmp || paymentAmount <= 0) return;

    // Check duplicate
    if (
      isDuplicatePayment(
        allPayments,
        paymentModalEmp.employeeId,
        paymentDate,
        paymentAmount,
        referenceNo
      )
    ) {
      setPaymentError('A payment with the exact same date, amount, and reference already exists.');
      sound.playAlert();
      return;
    }

    const summary = calculateEmployeeSummary(paymentModalEmp, allPayments);
    const newPayment: SalaryPaymentEntity = {
      paymentId: `sal_pay_${Date.now()}`,
      employeeId: paymentModalEmp.employeeId,
      companyId: 'total_solution',
      amount: paymentAmount,
      paymentDate,
      method: paymentMethod,
      referenceNo: referenceNo.trim(),
      notes: paymentNotes.trim(),
      cycleStart: summary.cycleStart,
      cycleEnd: summary.cycleEnd,
      createdAt: Date.now(),
      createdByUid: user.username,
    };

    AppDb.recordSalaryPayment(newPayment);
    sound.playSuccess();
    setPaymentModalEmp(null);
    onEmployeesUpdated();
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header */}
      <div className="bg-white p-4 sm:p-5 rounded-2xl shadow-sm border border-slate-200 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-2">
            <Users className="w-5 h-5 text-blue-600" />
            Technician & Staff Payroll Engine ({employees.length})
          </h2>
          <p className="text-xs text-slate-500">
            Work start-date anchored salary cycles, daily wage proration, attendance eligibility and payout slips
          </p>
        </div>

        {user.isAdmin && (
          <button
            onClick={openAddEmployee}
            className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/30 flex items-center gap-1.5 transition"
          >
            <Plus className="w-4 h-4" />
            Add Employee
          </button>
        )}
      </div>

      {/* Employees Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {employees.map((emp) => {
          const summary = calculateEmployeeSummary(emp, allPayments);
          const isFullyPaid = summary.status === 'FULLY_PAID';
          const isPending = summary.status === 'PENDING';

          return (
            <div
              key={emp.employeeId}
              className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 hover:border-blue-400 transition space-y-4 flex flex-col justify-between"
            >
              <div className="space-y-3">
                <div className="flex justify-between items-start">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs font-bold text-blue-900 bg-blue-50 px-2 py-0.5 rounded">
                        {emp.employeeCode}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-extrabold uppercase ${
                          isFullyPaid
                            ? 'bg-emerald-100 text-emerald-800'
                            : isPending
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-blue-100 text-blue-800'
                        }`}
                      >
                        {summary.status.replace(/_/g, ' ')}
                      </span>
                    </div>
                    <h3 className="font-bold text-base text-slate-900 mt-1">{emp.name}</h3>
                    {emp.mobile && (
                      <span className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                        <Phone className="w-3 h-3 text-emerald-600" /> {emp.mobile}
                      </span>
                    )}
                  </div>

                  {user.isAdmin && (
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => openEditEmployee(emp)}
                        className="p-1.5 text-slate-400 hover:text-blue-600 transition"
                      >
                        <FileText className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDeleteEmployee(emp.employeeId)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  )}
                </div>

                {/* Cycle Details */}
                <div className="bg-slate-50 rounded-xl p-3.5 border border-slate-200 text-xs space-y-2">
                  <div className="flex justify-between text-slate-500 font-medium">
                    <span>Active Salary Cycle:</span>
                    <span className="font-bold text-slate-800">
                      {summary.cycleStart} to {summary.cycleEnd}
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-2 pt-1 border-t border-slate-200">
                    <div>
                      <span className="text-[10px] text-slate-400 font-semibold block">Monthly CTC</span>
                      <span className="font-bold font-mono text-slate-900">{formatINR(emp.monthlySalary)}</span>
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-400 font-semibold block">Daily Rate</span>
                      <span className="font-bold font-mono text-slate-700">{formatINR(summary.dailySalary)}/day</span>
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-400 font-semibold block">Eligible Days</span>
                      <span className="font-bold text-blue-900">
                        {summary.eligibleDays} / {summary.calendarDaysInCycle} Days
                      </span>
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-400 font-semibold block">Earned Salary</span>
                      <span className="font-black font-mono text-slate-900">{formatINR(summary.earnedSalary)}</span>
                    </div>
                  </div>

                  <div className="pt-2 border-t border-slate-200 flex justify-between items-center text-xs">
                    <div>
                      <span className="text-[10px] text-emerald-700 font-bold block">Paid in Cycle</span>
                      <span className="font-bold font-mono text-emerald-700">{formatINR(summary.totalPaidInCycle)}</span>
                    </div>
                    <div className="text-right">
                      <span className="text-[10px] text-rose-600 font-bold block">Remaining Due</span>
                      <span className="font-black font-mono text-rose-700 text-sm">
                        {formatINR(summary.remainingBalance)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="pt-2 flex justify-between items-center gap-2">
                <button
                  onClick={() => setSlipModalEmp(emp)}
                  className="px-3 py-1.5 border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
                >
                  <FileText className="w-3.5 h-3.5 text-slate-500" />
                  Salary Slip
                </button>

                {user.isAdmin && (
                  <button
                    onClick={() => openRecordPayment(emp, summary.remainingBalance)}
                    className="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-lg flex items-center gap-1.5 shadow transition"
                  >
                    <DollarSign className="w-3.5 h-3.5" />
                    Record Payout
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Record Payment Modal */}
      {paymentModalEmp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-slate-200">
            <div className="bg-gradient-to-r from-emerald-800 to-teal-900 text-white p-5 flex justify-between items-center">
              <div>
                <h3 className="font-bold text-base">Record Salary Payment</h3>
                <p className="text-xs text-emerald-200">{paymentModalEmp.name}</p>
              </div>
              <button
                onClick={() => setPaymentModalEmp(null)}
                className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSavePayment} className="p-6 space-y-4 text-xs">
              {paymentError && (
                <div className="p-2.5 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 font-semibold flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0 text-rose-500" />
                  <span>{paymentError}</span>
                </div>
              )}

              <div>
                <label className="block font-bold text-slate-700 mb-1">Disbursed Amount (₹) *</label>
                <input
                  type="number"
                  step="any"
                  required
                  min="1"
                  value={paymentAmount}
                  onChange={(e) => setPaymentAmount(parseFloat(e.target.value) || 0)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono font-bold text-base text-emerald-800"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-bold text-slate-700 mb-1">Payment Date</label>
                  <input
                    type="date"
                    required
                    value={paymentDate}
                    onChange={(e) => setPaymentDate(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Payment Mode</label>
                  <select
                    value={paymentMethod}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none bg-white font-medium"
                  >
                    <option value="Bank Transfer">Bank Transfer / NEFT</option>
                    <option value="UPI">UPI / GPay</option>
                    <option value="Cash">Cash in Hand</option>
                    <option value="Cheque">Cheque</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block font-bold text-slate-700 mb-1">Reference / UTR / Txn No</label>
                <input
                  type="text"
                  placeholder="e.g. UTR-99882211"
                  value={referenceNo}
                  onChange={(e) => setReferenceNo(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono"
                />
              </div>

              <div>
                <label className="block font-bold text-slate-700 mb-1">Notes / Disbursal Memo</label>
                <textarea
                  rows={2}
                  placeholder="e.g. Monthly salary disbursed for November cycle"
                  value={paymentNotes}
                  onChange={(e) => setPaymentNotes(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setPaymentModalEmp(null)}
                  className="px-4 py-2 border border-slate-300 text-slate-700 font-semibold rounded-xl hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow"
                >
                  <Check className="w-4 h-4" />
                  Confirm Payout
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Salary Slip Modal */}
      {slipModalEmp && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full p-6 space-y-4">
            <div className="flex justify-between items-center border-b pb-3">
              <div>
                <h3 className="font-bold text-base text-slate-900">Official Salary Slip</h3>
                <p className="text-xs text-slate-500">TOTAL SOLUTION • Confidential</p>
              </div>
              <button
                onClick={() => setSlipModalEmp(null)}
                className="p-1.5 rounded-lg border text-slate-600 hover:bg-slate-100"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {(() => {
              const summary = calculateEmployeeSummary(slipModalEmp, allPayments);
              return (
                <div className="space-y-3 text-xs">
                  <div className="p-3 bg-slate-50 rounded-xl border space-y-1">
                    <div className="flex justify-between font-bold text-slate-800">
                      <span>Employee: {slipModalEmp.name}</span>
                      <span>Code: {slipModalEmp.employeeCode}</span>
                    </div>
                    <div className="flex justify-between text-slate-600">
                      <span>Salary Cycle:</span>
                      <span>{summary.cycleStart} to {summary.cycleEnd}</span>
                    </div>
                    <div className="flex justify-between text-slate-600">
                      <span>Work Start Date:</span>
                      <span>{slipModalEmp.workStartDate}</span>
                    </div>
                  </div>

                  <table className="w-full border-collapse border border-slate-200">
                    <tbody>
                      <tr className="border-b border-slate-200">
                        <td className="p-2 font-semibold">Monthly Agreed CTC:</td>
                        <td className="p-2 text-right font-mono font-bold">{formatINR(slipModalEmp.monthlySalary)}</td>
                      </tr>
                      <tr className="border-b border-slate-200">
                        <td className="p-2 font-semibold">Calendar Days in Cycle:</td>
                        <td className="p-2 text-right">{summary.calendarDaysInCycle} Days</td>
                      </tr>
                      <tr className="border-b border-slate-200">
                        <td className="p-2 font-semibold">Eligible Worked Days:</td>
                        <td className="p-2 text-right">{summary.eligibleDays} Days</td>
                      </tr>
                      <tr className="border-b border-slate-200">
                        <td className="p-2 font-semibold">Daily Wage:</td>
                        <td className="p-2 text-right font-mono">{formatINR(summary.dailySalary)}</td>
                      </tr>
                      <tr className="border-b border-slate-200 bg-blue-50/50">
                        <td className="p-2 font-bold text-blue-900">Total Earned Salary:</td>
                        <td className="p-2 text-right font-mono font-black text-blue-900">{formatINR(summary.earnedSalary)}</td>
                      </tr>
                      <tr className="border-b border-slate-200 text-emerald-800">
                        <td className="p-2 font-semibold">Total Paid in Cycle:</td>
                        <td className="p-2 text-right font-mono font-bold">{formatINR(summary.totalPaidInCycle)}</td>
                      </tr>
                      <tr className="bg-rose-50/50 text-rose-800">
                        <td className="p-2 font-bold">Remaining Balance Due:</td>
                        <td className="p-2 text-right font-mono font-black">{formatINR(summary.remainingBalance)}</td>
                      </tr>
                    </tbody>
                  </table>

                  <div className="flex justify-end gap-2 pt-2">
                    <button
                      onClick={() => window.print()}
                      className="px-4 py-2 bg-blue-600 text-white font-bold rounded-xl text-xs hover:bg-blue-700"
                    >
                      Print Slip
                    </button>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>
      )}

      {/* Add / Edit Employee Modal */}
      {employeeModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200">
            <div className="bg-gradient-to-r from-blue-900 to-indigo-900 text-white p-5 flex justify-between items-center">
              <h3 className="font-bold text-base">
                {editingEmp ? 'Edit Technician Profile' : 'Add New Staff / Technician'}
              </h3>
              <button
                onClick={() => setEmployeeModalOpen(false)}
                className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveEmployee} className="p-6 space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-bold text-slate-700 mb-1">Employee Code *</label>
                  <input
                    type="text"
                    required
                    value={empCode}
                    onChange={(e) => setEmpCode(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono font-bold"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Monthly Salary (₹) *</label>
                  <input
                    type="number"
                    step="any"
                    required
                    min="0"
                    value={monthlySalary}
                    onChange={(e) => setMonthlySalary(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono font-bold"
                  />
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Full Name *</label>
                  <input
                    type="text"
                    required
                    value={empName}
                    onChange={(e) => setEmpName(e.target.value)}
                    placeholder="e.g. Ramesh Verma (Senior Tech)"
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-semibold focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Mobile / WhatsApp</label>
                  <input
                    type="tel"
                    value={empMobile}
                    onChange={(e) => setEmpMobile(e.target.value)}
                    placeholder="e.g. 9823123456"
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-medium"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Work Start Date *</label>
                  <input
                    type="date"
                    required
                    value={workStartDate}
                    onChange={(e) => setWorkStartDate(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-medium"
                  />
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Home Address</label>
                  <input
                    type="text"
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                    placeholder="Residence address..."
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none"
                  />
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Notes / Certifications</label>
                  <textarea
                    rows={2}
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    placeholder="Skills, camera certifications, joining details..."
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setEmployeeModalOpen(false)}
                  className="px-4 py-2 border border-slate-300 text-slate-700 font-semibold rounded-xl hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow"
                >
                  <Check className="w-4 h-4" />
                  Save Record
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
