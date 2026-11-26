import React, { useState } from 'react';
import {
  BillDraft,
  BillEntity,
  CompanySettings,
  UpiSettings,
  ProductEntity,
  CustomerEntity,
  ProductComboFile,
  UserSession,
  InvoiceItem,
  DocumentType,
  PaymentMode,
  ChargeType,
} from '../types';
import { AppDb } from '../storage/db';
import { formatINR } from '../utils/rupeesWords';
import { sound } from '../utils/soundEngine';
import { SignaturePad } from '../components/SignaturePad';
import { InvoiceSheetPreview } from '../components/InvoiceSheetPreview';
import { ExportOptionsModal } from '../components/ExportOptionsModal';
import { PaymentAndReviewDialog } from '../components/PaymentAndReviewDialog';
import {
  Plus,
  Trash2,
  FileText,
  Eye,
  CheckCircle,
  Package,
  Layers,
  RotateCcw,
  Star,
  MapPin,
  CreditCard,
  Banknote,
} from 'lucide-react';

interface BillScreenProps {
  currentUser: UserSession;
  company: CompanySettings;
  upi: UpiSettings;
  products: ProductEntity[];
  customers: CustomerEntity[];
  combos: ProductComboFile[];
  onBillGenerated: (bill: BillEntity) => void;
  prefillDraft?: Partial<BillDraft> | null;
}

export const BillScreen: React.FC<BillScreenProps> = ({
  currentUser,
  company,
  upi,
  products,
  customers,
  combos,
  onBillGenerated,
  prefillDraft,
}) => {
  const getInitialDraft = (): BillDraft => {
    const today = new Date().toISOString().slice(0, 10);
    return {
      documentType: prefillDraft?.documentType || 'BILL',
      invoiceNo: prefillDraft?.invoiceNo || AppDb.getNextInvoiceNo(prefillDraft?.documentType || 'BILL'),
      date: prefillDraft?.date || today,
      customerName: prefillDraft?.customerName || '',
      customerDetails: prefillDraft?.customerDetails || '',
      customerLocationUrl: prefillDraft?.customerLocationUrl || '',
      items: prefillDraft?.items && prefillDraft.items.length > 0
        ? prefillDraft.items
        : [
            {
              localId: Date.now(),
              folder: 'Hikvision',
              description: 'Hikvision 2MP HD 1080P Dome Camera (Night Vision)',
              qty: 1,
              unit: 'Nos',
              rate: 1650,
              amount: 1650,
            },
          ],
      subtotal: prefillDraft?.subtotal || 1650,
      discount: prefillDraft?.discount || 0,
      extraChargeType: prefillDraft?.extraChargeType || 'NONE',
      extraChargeAmount: prefillDraft?.extraChargeAmount || 0,
      secondaryChargeType: prefillDraft?.secondaryChargeType || 'NONE',
      secondaryChargeAmount: prefillDraft?.secondaryChargeAmount || 0,
      grandTotal: prefillDraft?.grandTotal || 1650,
      paidAmount: prefillDraft?.paidAmount || 1650,
      dueAmount: prefillDraft?.dueAmount || 0,
      paymentMode: prefillDraft?.paymentMode || 'ONLINE',
      signatureBase64: prefillDraft?.signatureBase64 || '',
      customQrText: '',
      staffRating: prefillDraft?.staffRating || 5,
      staffReviewNote: prefillDraft?.staffReviewNote || '',
      quotationNote: prefillDraft?.quotationNote || '',
      isHelpBill: prefillDraft?.isHelpBill || false,
      helpRequestId: prefillDraft?.helpRequestId || 0,
    };
  };

  const [draft, setDraft] = useState<BillDraft>(getInitialDraft);
  const [showPreview, setShowPreview] = useState<boolean>(false);
  const [customerSuggestions, setCustomerSuggestions] = useState<CustomerEntity[]>([]);
  const [selectedFolder, setSelectedFolder] = useState<string>('All');
  const [generatedBillForExport, setGeneratedBillForExport] = useState<BillEntity | null>(null);
  const [settlementBill, setSettlementBill] = useState<BillEntity | null>(null);

  // Recalculate totals
  const recalculate = (
    items: InvoiceItem[],
    extraChargeAmount: number,
    secondaryChargeAmount: number,
    discount: number,
    paidAmount: number
  ) => {
    const subtotal = items.reduce((acc, itm) => acc + (itm.qty * itm.rate || 0), 0);
    const grandTotal = Math.max(0, subtotal + extraChargeAmount + secondaryChargeAmount - discount);
    const due = Math.max(0, grandTotal - paidAmount);
    return { subtotal, grandTotal, dueAmount: due };
  };

  const updateDraftFields = (updates: Partial<BillDraft>) => {
    setDraft((prev) => {
      const merged = { ...prev, ...updates };
      const { subtotal, grandTotal, dueAmount } = recalculate(
        merged.items,
        merged.extraChargeAmount,
        merged.secondaryChargeAmount,
        merged.discount,
        merged.paidAmount
      );
      return { ...merged, subtotal, grandTotal, dueAmount };
    });
  };

  // Change Document Type
  const handleDocumentTypeChange = (docType: DocumentType) => {
    sound.playClick();
    const newNo = AppDb.getNextInvoiceNo(docType);
    updateDraftFields({
      documentType: docType,
      invoiceNo: newNo,
      paidAmount: docType === 'QUOTATION' ? 0 : draft.grandTotal,
    });
  };

  // Customer Autocomplete
  const handleCustomerNameChange = (name: string) => {
    updateDraftFields({ customerName: name });
    if (name.trim().length >= 2) {
      const filtered = customers.filter((c) =>
        c.name.toLowerCase().includes(name.toLowerCase()) ||
        c.phone.includes(name)
      );
      setCustomerSuggestions(filtered);
    } else {
      setCustomerSuggestions([]);
    }
  };

  const selectCustomer = (c: CustomerEntity) => {
    sound.playClick();
    updateDraftFields({
      customerName: c.name,
      customerDetails: c.details || `Phone: ${c.phone}\n${c.address}`,
      customerLocationUrl: c.locationUrl || '',
    });
    setCustomerSuggestions([]);
  };

  // Item management
  const handleItemChange = (index: number, field: keyof InvoiceItem, value: unknown) => {
    const updated = [...draft.items];
    const target = { ...updated[index], [field]: value };
    if (field === 'qty' || field === 'rate') {
      target.amount = Math.max(0, target.qty * target.rate);
    }
    updated[index] = target;
    updateDraftFields({ items: updated });
  };

  const addItemRow = () => {
    sound.playClick();
    const newItem: InvoiceItem = {
      localId: Date.now(),
      folder: 'General',
      description: '',
      qty: 1,
      unit: 'Nos',
      rate: 0,
      amount: 0,
    };
    updateDraftFields({ items: [...draft.items, newItem] });
  };

  const removeItemRow = (index: number) => {
    sound.playClick();
    const updated = draft.items.filter((_, i) => i !== index);
    updateDraftFields({ items: updated });
  };

  const addCatalogProduct = (prod: ProductEntity) => {
    sound.playClick();
    const newItem: InvoiceItem = {
      localId: Date.now(),
      folder: prod.folder,
      description: prod.name,
      qty: 1,
      unit: prod.unit,
      rate: prod.defaultRate,
      amount: prod.defaultRate,
    };
    updateDraftFields({ items: [...draft.items, newItem] });
  };

  const addComboPackage = (combo: ProductComboFile) => {
    sound.playClick();
    const newItems: InvoiceItem[] = combo.items.map((itm, i) => ({
      localId: Date.now() + i,
      folder: itm.folder || 'Combo',
      description: itm.productName,
      qty: itm.qty,
      unit: itm.unit,
      rate: itm.catalogRate,
      amount: itm.qty * itm.catalogRate,
    }));
    updateDraftFields({ items: [...draft.items, ...newItems] });
  };

  // Generate & Save Bill
  const handleGenerateBill = () => {
    if (!draft.customerName.trim()) {
      alert('Please enter customer name');
      sound.playAlert();
      return;
    }
    if (draft.items.length === 0 || draft.items.every((itm) => itm.amount <= 0)) {
      alert('Please add at least one item with a valid rate');
      sound.playAlert();
      return;
    }

    const billEntity: BillEntity = {
      id: `bill_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      invoiceNo: draft.invoiceNo.trim(),
      date: draft.date,
      customerName: draft.customerName.trim(),
      customerDetails: draft.customerDetails.trim(),
      itemsJson: JSON.stringify(draft.items),
      subtotal: draft.subtotal,
      discount: draft.discount,
      grandTotal: draft.grandTotal,
      paidAmount: draft.documentType === 'QUOTATION' ? 0 : draft.paidAmount,
      dueAmount: draft.documentType === 'QUOTATION' ? 0 : draft.dueAmount,
      paymentMode: draft.paymentMode,
      signatureBase64: draft.signatureBase64,
      customQrText: draft.customQrText,
      createdBy: currentUser.username,
      updatedAt: Date.now(),
      extraChargeType: draft.extraChargeType,
      extraChargeAmount: draft.extraChargeAmount,
      secondaryChargeType: draft.secondaryChargeType,
      secondaryChargeAmount: draft.secondaryChargeAmount,
      staffRating: draft.staffRating,
      staffReviewNote: draft.staffReviewNote,
      documentType: draft.documentType,
      isHelpBill: draft.isHelpBill,
      helpRequestId: draft.helpRequestId,
      quotationNote: draft.quotationNote,
      paymentStatus:
        draft.documentType === 'QUOTATION'
          ? 'UNPAID'
          : draft.paidAmount >= draft.grandTotal - 0.01
          ? 'PAID'
          : draft.paidAmount > 0
          ? 'PARTIAL'
          : 'UNPAID',
      linkedPaymentId: '',
      stockDeducted: draft.documentType === 'BILL',
    };

    // Save to Database
    AppDb.saveBill(billEntity);

    // Deduct Stock if Tax Invoice
    if (billEntity.documentType === 'BILL') {
      AppDb.deductProductStock(draft.items);
    }

    sound.playSuccess();
    sound.speak(
      `${draft.documentType === 'QUOTATION' ? 'Quotation' : 'Bill'} generated. Total amount is ${Math.round(
        draft.grandTotal
      )} rupees.`
    );

    onBillGenerated(billEntity);
    setGeneratedBillForExport(billEntity);
  };

  const handleReset = () => {
    sound.playClick();
    setDraft(getInitialDraft());
  };

  const folders = ['All', 'Hikvision', 'Dahua', 'Power Supply', 'Hard Disk', 'Cable', 'Accessories', 'General'];
  const filteredProducts =
    selectedFolder === 'All'
      ? products
      : products.filter((p) => p.folder.toLowerCase() === selectedFolder.toLowerCase());

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Top Header Actions */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 bg-white p-4 rounded-2xl shadow-sm border border-slate-200">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-2">
            <FileText className="w-5 h-5 text-blue-600" />
            {draft.documentType === 'QUOTATION' ? 'Create Quotation' : 'New Tax Invoice & Bill'}
          </h2>
          <p className="text-xs text-slate-500">
            Generate invoice, dynamic UPI QR payment, customer signature & printable A4 sheet
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {/* Document Type Toggle */}
          <div className="flex bg-slate-100 p-1 rounded-xl border border-slate-200">
            <button
              onClick={() => handleDocumentTypeChange('BILL')}
              className={`px-3.5 py-1.5 text-xs font-bold rounded-lg transition ${
                draft.documentType === 'BILL'
                  ? 'bg-blue-600 text-white shadow'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Tax Invoice
            </button>
            <button
              onClick={() => handleDocumentTypeChange('QUOTATION')}
              className={`px-3.5 py-1.5 text-xs font-bold rounded-lg transition ${
                draft.documentType === 'QUOTATION'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Quotation
            </button>
          </div>

          <button
            onClick={() => setShowPreview(!showPreview)}
            className={`flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold rounded-xl border transition ${
              showPreview
                ? 'bg-blue-50 border-blue-400 text-blue-700'
                : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'
            }`}
          >
            <Eye className="w-4 h-4 text-blue-600" />
            {showPreview ? 'Hide Sheet' : 'A4 Sheet Preview'}
          </button>

          <button
            onClick={handleReset}
            className="flex items-center gap-1 px-3 py-2 text-xs font-semibold rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 transition"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            Reset
          </button>
        </div>
      </div>

      {/* Main Grid: Form Left, Preview Right (or toggle) */}
      <div className={`grid grid-cols-1 ${showPreview ? 'lg:grid-cols-2' : ''} gap-6 items-start`}>
        {/* FORM PANEL */}
        <div className="space-y-5">
          {/* 1. Customer & Invoice Details */}
          <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-4">
            <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              1. Customer & Document Meta
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="relative">
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Customer / Business Name *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Dr. Rajesh Naik / Villa Candolim"
                  value={draft.customerName}
                  onChange={(e) => handleCustomerNameChange(e.target.value)}
                  className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm font-semibold outline-none focus:ring-2 focus:ring-blue-500"
                />

                {/* Autocomplete Dropdown */}
                {customerSuggestions.length > 0 && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-slate-200 rounded-xl shadow-xl z-20 max-h-48 overflow-y-auto divide-y divide-slate-100">
                    {customerSuggestions.map((c) => (
                      <div
                        key={c.id}
                        onClick={() => selectCustomer(c)}
                        className="p-2.5 hover:bg-blue-50 cursor-pointer text-xs"
                      >
                        <div className="font-bold text-slate-800">{c.name}</div>
                        <div className="text-[11px] text-slate-500">{c.phone} • {c.address}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  {draft.documentType === 'QUOTATION' ? 'Quotation Number' : 'Invoice Number'}
                </label>
                <input
                  type="text"
                  value={draft.invoiceNo}
                  onChange={(e) => updateDraftFields({ invoiceNo: e.target.value })}
                  className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm font-mono font-bold text-blue-900 bg-slate-50 outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Date</label>
                <input
                  type="date"
                  value={draft.date}
                  onChange={(e) => updateDraftFields({ date: e.target.value })}
                  className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm font-medium outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Google Maps Location URL</label>
                <div className="relative">
                  <MapPin className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                  <input
                    type="url"
                    placeholder="https://maps.google.com/..."
                    value={draft.customerLocationUrl}
                    onChange={(e) => updateDraftFields({ customerLocationUrl: e.target.value })}
                    className="w-full pl-9 pr-3 py-2 border border-slate-300 rounded-xl text-xs outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>

              <div className="sm:col-span-2">
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Customer Details / Phone & Address
                </label>
                <textarea
                  rows={2}
                  placeholder="Phone: 9822114455&#10;Naika Vaddo, Calangute, North Goa 403516"
                  value={draft.customerDetails}
                  onChange={(e) => updateDraftFields({ customerDetails: e.target.value })}
                  className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-xs outline-none focus:ring-2 focus:ring-blue-500 leading-relaxed"
                />
              </div>
            </div>
          </div>

          {/* 2. Product Catalog Quick Select & Combo Packages */}
          <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-3.5">
            <div className="flex justify-between items-center flex-wrap gap-2">
              <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                <Package className="w-4 h-4 text-blue-600" />
                2. CCTV Catalog & Combo Packages
              </h3>
              <div className="flex gap-1 overflow-x-auto pb-1 max-w-full">
                {folders.map((f) => (
                  <button
                    key={f}
                    type="button"
                    onClick={() => setSelectedFolder(f)}
                    className={`px-2 py-0.5 rounded-lg text-[11px] font-bold whitespace-nowrap transition ${
                      selectedFolder === f
                        ? 'bg-blue-600 text-white'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </div>

            {/* Quick Catalog Item Chips */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-40 overflow-y-auto pr-1">
              {filteredProducts.slice(0, 12).map((prod) => (
                <button
                  key={prod.id}
                  type="button"
                  onClick={() => addCatalogProduct(prod)}
                  className="p-2 border border-slate-200 hover:border-blue-500 hover:bg-blue-50/50 rounded-xl text-left transition group flex flex-col justify-between"
                >
                  <span className="text-[11px] font-semibold text-slate-800 line-clamp-1 group-hover:text-blue-700">
                    {prod.name}
                  </span>
                  <div className="flex justify-between items-center text-[10px] mt-1">
                    <span className="font-mono font-bold text-emerald-700">₹{prod.defaultRate}</span>
                    <span className="text-slate-400 font-medium">Stock: {prod.stockQty}</span>
                  </div>
                </button>
              ))}
            </div>

            {/* Combos list */}
            {combos.length > 0 && (
              <div className="pt-2 border-t border-slate-100 flex items-center gap-2 overflow-x-auto">
                <span className="text-[10px] font-extrabold uppercase text-indigo-700 flex items-center gap-1 shrink-0">
                  <Layers className="w-3.5 h-3.5" /> Combos:
                </span>
                {combos.map((c) => (
                  <button
                    key={c.comboFileId}
                    type="button"
                    onClick={() => addComboPackage(c)}
                    className="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg text-[10px] font-bold text-indigo-800 whitespace-nowrap transition"
                  >
                    + {c.name} ({formatINR(c.comboPrice)})
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 3. Items Table */}
          <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-4">
            <div className="flex justify-between items-center">
              <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                3. Invoice Items & Bill Rows ({draft.items.length})
              </h3>
              <button
                type="button"
                onClick={addItemRow}
                className="flex items-center gap-1 px-3 py-1 bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold text-xs rounded-lg transition"
              >
                <Plus className="w-3.5 h-3.5" />
                Add Custom Row
              </button>
            </div>

            <div className="space-y-2.5">
              {draft.items.map((item, idx) => (
                <div
                  key={item.localId || idx}
                  className="p-3 bg-slate-50/80 border border-slate-200 rounded-xl space-y-2"
                >
                  <div className="flex items-center gap-2">
                    <span className="w-5 text-center text-xs font-bold text-slate-400">
                      {idx + 1}
                    </span>
                    <input
                      type="text"
                      placeholder="Product Description"
                      value={item.description}
                      onChange={(e) => handleItemChange(idx, 'description', e.target.value)}
                      className="flex-1 px-3 py-1.5 border border-slate-300 rounded-lg text-xs font-medium bg-white outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <button
                      type="button"
                      onClick={() => removeItemRow(idx)}
                      className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                      title="Remove Row"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>

                  <div className="grid grid-cols-4 gap-2 pl-7">
                    <div>
                      <span className="text-[10px] text-slate-500 font-semibold block mb-0.5">Qty</span>
                      <input
                        type="number"
                        step="any"
                        min="0"
                        value={item.qty}
                        onChange={(e) => handleItemChange(idx, 'qty', parseFloat(e.target.value) || 0)}
                        className="w-full px-2 py-1 border border-slate-300 rounded-lg text-xs text-center font-bold bg-white outline-none"
                      />
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-500 font-semibold block mb-0.5">Unit</span>
                      <input
                        type="text"
                        value={item.unit}
                        onChange={(e) => handleItemChange(idx, 'unit', e.target.value)}
                        className="w-full px-2 py-1 border border-slate-300 rounded-lg text-xs text-center bg-white outline-none"
                      />
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-500 font-semibold block mb-0.5">Rate (₹)</span>
                      <input
                        type="number"
                        step="any"
                        min="0"
                        value={item.rate}
                        onChange={(e) => handleItemChange(idx, 'rate', parseFloat(e.target.value) || 0)}
                        className="w-full px-2 py-1 border border-slate-300 rounded-lg text-xs text-right font-mono font-bold bg-white outline-none"
                      />
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-500 font-semibold block mb-0.5">Amount (₹)</span>
                      <div className="px-2 py-1 bg-slate-200/80 rounded-lg text-xs font-mono font-bold text-right text-slate-800">
                        {formatINR(item.amount)}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* 4. Charges, Discount & Totals */}
          <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-4">
            <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              4. Additional Charges, Discount & Payment
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* Extra Charge 1 */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Extra Charges Type</label>
                <select
                  value={draft.extraChargeType}
                  onChange={(e) => updateDraftFields({ extraChargeType: e.target.value as ChargeType })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs font-medium outline-none bg-white"
                >
                  <option value="NONE">None</option>
                  <option value="SERVICE">Service Charges</option>
                  <option value="INSTALLATION">Installation Charges</option>
                  <option value="INSTALLATION_AND_SERVICE">Installation & Service Charges</option>
                  <option value="CUSTOMER_CARE_AND_SUPPORT">Customer Care & Support Charges</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Extra Charges Amount (₹)</label>
                <input
                  type="number"
                  step="any"
                  min="0"
                  value={draft.extraChargeAmount}
                  onChange={(e) => updateDraftFields({ extraChargeAmount: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs font-mono font-bold outline-none"
                />
              </div>

              {/* Discount */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Discount (₹)</label>
                <input
                  type="number"
                  step="any"
                  min="0"
                  value={draft.discount}
                  onChange={(e) => updateDraftFields({ discount: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs font-mono font-bold text-rose-600 outline-none"
                />
              </div>

              {/* Payment Mode */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Payment Mode</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => updateDraftFields({ paymentMode: 'ONLINE' })}
                    className={`py-2 px-2.5 rounded-xl border text-xs font-bold flex items-center justify-center gap-1.5 transition ${
                      draft.paymentMode === 'ONLINE'
                        ? 'border-blue-600 bg-blue-50 text-blue-800'
                        : 'border-slate-200 text-slate-600'
                    }`}
                  >
                    <CreditCard className="w-3.5 h-3.5 text-blue-600" />
                    Online / UPI
                  </button>
                  <button
                    type="button"
                    onClick={() => updateDraftFields({ paymentMode: 'CASH' })}
                    className={`py-2 px-2.5 rounded-xl border text-xs font-bold flex items-center justify-center gap-1.5 transition ${
                      draft.paymentMode === 'CASH'
                        ? 'border-emerald-600 bg-emerald-50 text-emerald-800'
                        : 'border-slate-200 text-slate-600'
                    }`}
                  >
                    <Banknote className="w-3.5 h-3.5 text-emerald-600" />
                    Cash
                  </button>
                </div>
              </div>

              {/* Paid Amount (if Bill) */}
              {draft.documentType === 'BILL' && (
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="text-xs font-bold text-slate-700">Paid Amount (₹)</label>
                    <button
                      type="button"
                      onClick={() => updateDraftFields({ paidAmount: draft.grandTotal })}
                      className="text-[10px] text-blue-600 font-bold hover:underline"
                    >
                      Fill Grand Total
                    </button>
                  </div>
                  <input
                    type="number"
                    step="any"
                    min="0"
                    max={draft.grandTotal}
                    value={draft.paidAmount}
                    onChange={(e) => updateDraftFields({ paidAmount: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs font-mono font-bold text-emerald-700 outline-none"
                  />
                </div>
              )}
            </div>

            {/* Calculations Card */}
            <div className="mt-4 p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-2">
              <div className="flex justify-between text-xs font-semibold text-slate-600">
                <span>Subtotal:</span>
                <span className="font-mono">{formatINR(draft.subtotal)}</span>
              </div>
              {draft.extraChargeAmount > 0 && (
                <div className="flex justify-between text-xs font-semibold text-slate-600">
                  <span>Extra Charges:</span>
                  <span className="font-mono">+{formatINR(draft.extraChargeAmount)}</span>
                </div>
              )}
              {draft.discount > 0 && (
                <div className="flex justify-between text-xs font-semibold text-rose-600">
                  <span>Discount:</span>
                  <span className="font-mono">-{formatINR(draft.discount)}</span>
                </div>
              )}
              <div className="flex justify-between text-sm font-black text-slate-900 pt-2 border-t border-slate-200">
                <span>{draft.documentType === 'QUOTATION' ? 'Quotation Total:' : 'Grand Total:'}</span>
                <span className="font-mono text-base text-blue-900">{formatINR(draft.grandTotal)}</span>
              </div>
              {draft.documentType === 'BILL' && (
                <div className="flex justify-between text-xs font-bold pt-1">
                  <span className="text-rose-600">Due Balance:</span>
                  <span className="font-mono text-rose-600">{formatINR(draft.dueAmount)}</span>
                </div>
              )}
            </div>
          </div>

          {/* 5. Customer Signature & Staff Rating */}
          <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 space-y-4">
            <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              5. Customer Signature & Review
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1.5">
                  Customer Signature Pad
                </label>
                <SignaturePad
                  initialSignature={draft.signatureBase64}
                  onSave={(base64) => updateDraftFields({ signatureBase64: base64 })}
                  onClear={() => updateDraftFields({ signatureBase64: '' })}
                />
              </div>

              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Staff / Work Rating</label>
                  <div className="flex items-center gap-1.5">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <button
                        key={star}
                        type="button"
                        onClick={() => updateDraftFields({ staffRating: star })}
                        className="p-1 hover:scale-110 transition"
                      >
                        <Star
                          className={`w-6 h-6 ${
                            star <= draft.staffRating
                              ? 'fill-amber-400 text-amber-500'
                              : 'text-slate-300'
                          }`}
                        />
                      </button>
                    ))}
                    <span className="text-xs font-bold text-slate-700 ml-2">
                      {draft.staffRating}.0 ★
                    </span>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Staff Review & Service Note
                  </label>
                  <textarea
                    rows={3}
                    placeholder="e.g. CCTV Camera viewing verified on TV & Android mobile."
                    value={draft.staffReviewNote}
                    onChange={(e) => updateDraftFields({ staffReviewNote: e.target.value })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* GENERATE BUTTON */}
          <button
            type="button"
            onClick={handleGenerateBill}
            className="w-full py-4 bg-gradient-to-r from-blue-700 to-indigo-700 hover:from-blue-800 hover:to-indigo-800 text-white font-black text-base rounded-2xl shadow-xl shadow-blue-700/25 flex items-center justify-center gap-2 transition hover:scale-[1.01] active:scale-[0.99]"
          >
            <CheckCircle className="w-5 h-5 text-emerald-300" />
            Generate & Commit {draft.documentType === 'QUOTATION' ? 'Quotation' : 'Tax Invoice'}
          </button>
        </div>

        {/* PREVIEW PANEL (Sticky on desktop) */}
        {showPreview && (
          <div className="lg:sticky lg:top-20 space-y-3">
            <div className="flex justify-between items-center bg-slate-900 text-white px-4 py-2.5 rounded-xl text-xs font-bold">
              <span>Live True A4 Printable Document Sheet</span>
              <button
                onClick={() => window.print()}
                className="px-2.5 py-1 bg-blue-600 hover:bg-blue-700 rounded-lg transition"
              >
                Print / Save PDF
              </button>
            </div>
            <InvoiceSheetPreview draft={draft} company={company} upi={upi} />
          </div>
        )}
      </div>

      {/* Export Options Modal after Generation */}
      {generatedBillForExport && (
        <ExportOptionsModal
          bill={generatedBillForExport}
          company={company}
          upi={upi}
          onDismiss={() => setGeneratedBillForExport(null)}
          onPrintA4={() => {
            setShowPreview(true);
            setTimeout(() => window.print(), 200);
          }}
          onOpenPaymentReview={() => setSettlementBill(generatedBillForExport)}
        />
      )}

      {/* Settlement & Review Dialog */}
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
            };
            AppDb.saveBill(updatedBill);
            setSettlementBill(null);
            sound.playSuccess();
          }}
        />
      )}
    </div>
  );
};
