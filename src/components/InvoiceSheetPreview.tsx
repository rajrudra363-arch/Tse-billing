import React, { useEffect, useState } from 'react';
import { BillDraft, CompanySettings, UpiSettings, DocumentType } from '../types';
import { convertRupeesToWords, formatINR, formatNumberINR } from '../utils/rupeesWords';
import { buildUpiUri, buildWhatsAppSupportUri, generateQrDataUrl } from '../utils/qrGenerator';

interface InvoiceSheetPreviewProps {
  draft: BillDraft;
  company: CompanySettings;
  upi: UpiSettings;
}

export const InvoiceSheetPreview: React.FC<InvoiceSheetPreviewProps> = ({ draft, company, upi }) => {
  const isQuotation = draft.documentType === 'QUOTATION';
  const [waQrUrl, setWaQrUrl] = useState<string>('');
  const [upiQrUrl, setUpiQrUrl] = useState<string>('');

  useEffect(() => {
    // Generate WhatsApp Support QR
    const waUri = buildWhatsAppSupportUri(company.mobile);
    if (waUri) {
      generateQrDataUrl(waUri, 180).then(setWaQrUrl);
    }
  }, [company.mobile]);

  useEffect(() => {
    // Generate Static UPI QR for preview/print (amount-free or invoice tagged)
    if (upi.upiId.trim()) {
      const upiUri = buildUpiUri(upi.upiId, upi.payeeName, 0, draft.invoiceNo);
      generateQrDataUrl(upiUri, 200).then(setUpiQrUrl);
    }
  }, [upi.upiId, upi.payeeName, draft.invoiceNo]);

  // Combine items with extra charges for official print display
  const effectiveItems = [...draft.items];
  if (draft.extraChargeType !== 'NONE' && draft.extraChargeAmount > 0) {
    const chargeLabel =
      draft.extraChargeType === 'SERVICE'
        ? 'Service Charges'
        : draft.extraChargeType === 'INSTALLATION'
        ? 'Installation Charges'
        : draft.extraChargeType === 'INSTALLATION_AND_SERVICE'
        ? 'Installation & Service Charges'
        : 'Customer Care & Support Charges';
    effectiveItems.push({
      localId: 999901,
      folder: 'Service',
      description: chargeLabel,
      qty: 1,
      unit: 'Job',
      rate: draft.extraChargeAmount,
      amount: draft.extraChargeAmount,
    });
  }
  if (draft.secondaryChargeType !== 'NONE' && draft.secondaryChargeAmount > 0) {
    const secLabel =
      draft.secondaryChargeType === 'SERVICE'
        ? 'Service Charges'
        : draft.secondaryChargeType === 'INSTALLATION'
        ? 'Installation Charges'
        : draft.secondaryChargeType === 'INSTALLATION_AND_SERVICE'
        ? 'Installation & Service Charges'
        : 'Customer Care & Support Charges';
    effectiveItems.push({
      localId: 999902,
      folder: 'Service',
      description: secLabel,
      qty: 1,
      unit: 'Job',
      rate: draft.secondaryChargeAmount,
      amount: draft.secondaryChargeAmount,
    });
  }

  const bannerTitle = isQuotation
    ? 'QUOTATION'
    : draft.isHelpBill
    ? 'CUSTOMER CARE & SUPPORT SERVICE INVOICE'
    : 'TAX INVOICE';

  return (
    <div
      id="printable-invoice"
      className="invoice-printable bg-white text-slate-900 border-[1.5px] border-black p-3 sm:p-5 text-[11px] leading-relaxed shadow-lg max-w-[850px] mx-auto select-text font-['Plus_Jakarta_Sans',sans-serif]"
    >
      {/* 1. TOP HEADER */}
      <div className="border border-black flex flex-col sm:flex-row justify-between items-stretch">
        <div className="flex-1 p-2 sm:p-3 flex items-center gap-3">
          <img
            src={company.logoBase64 || '/img_tse_logo.jpg'}
            alt="Logo"
            className="w-14 h-14 sm:w-16 sm:h-16 object-contain rounded border border-slate-200"
            onError={(e) => {
              (e.target as HTMLImageElement).src = '/ic_launcher_cctv_fg.png';
            }}
          />
          <div>
            <h1 className="text-base sm:text-lg font-black tracking-tight text-[#1E3A8A] uppercase">
              {company.companyName}
            </h1>
            <p className="text-[10px] font-bold text-[#2563EB]">
              CCTV Security • Sales • Installation • Support
            </p>
            <p className="text-[9.5px] font-semibold text-black mt-0.5">
              Mob/WA: {company.mobile} | {company.email}
            </p>
            <p className="text-[8.5px] text-slate-700 leading-tight">
              Office Address: {company.address}
            </p>
          </div>
        </div>

        {!isQuotation && (
          <div className="sm:border-l border-t sm:border-t-0 border-black p-2 sm:p-3 flex flex-col items-center justify-center text-center bg-slate-50 min-w-[150px]">
            <span className="text-[9px] font-bold text-[#1E3A8A]">Customer Care & Support</span>
            {waQrUrl ? (
              <img src={waQrUrl} alt="WhatsApp Support QR" className="w-14 h-14 my-1" />
            ) : (
              <div className="w-14 h-14 bg-slate-200 my-1 rounded" />
            )}
            <span className="text-[8px] text-slate-700 font-medium">
              Scan to WhatsApp us<br />{company.mobile}
            </span>
          </div>
        )}
      </div>

      {/* 2. DOCUMENT BANNER */}
      <div className="my-2 border-[1.5px] border-black bg-slate-100 py-1 px-3 text-center">
        <h2 className="text-xs sm:text-sm font-black tracking-widest text-black">
          {bannerTitle}
        </h2>
      </div>

      {/* 3. META ROW */}
      <div className="border border-black flex flex-col sm:flex-row justify-between mb-2">
        <div className="flex-1 p-2 border-b sm:border-b-0 sm:border-r border-black">
          <span className="font-bold text-[10px] text-black">M/S :</span>
          <p className="font-bold text-[11px] text-slate-900 mt-0.5">
            {draft.customerName || 'Walk-in Customer'}
          </p>
          {draft.customerDetails && (
            <p className="text-[9px] text-slate-700 whitespace-pre-line mt-0.5">
              {draft.customerDetails}
            </p>
          )}
        </div>
        <div className="w-full sm:w-64 p-2 flex flex-col justify-center space-y-1 bg-slate-50">
          <div className="flex justify-between font-bold text-[9.5px]">
            <span>DATE:</span>
            <span>{draft.date}</span>
          </div>
          <div className="flex justify-between font-bold text-[9.5px]">
            <span>{isQuotation ? 'QUOTATION NO.:' : 'INVOICE NO.:'}</span>
            <span className="text-[#1E3A8A]">{draft.invoiceNo}</span>
          </div>
          {draft.isHelpBill && draft.helpRequestId > 0 && !isQuotation && (
            <div className="flex justify-between font-bold text-[9px] text-blue-600">
              <span>SUPPORT TICKET:</span>
              <span>#{draft.helpRequestId}</span>
            </div>
          )}
        </div>
      </div>

      {/* 4. ITEMS TABLE */}
      <div className="border border-black overflow-x-auto">
        <table className="w-full border-collapse text-[9.5px]">
          <thead>
            <tr className="bg-slate-200 border-b border-black text-black font-bold">
              <th className="py-1 px-1.5 border-r border-black w-8 text-center">Sr</th>
              <th className="py-1 px-2 border-r border-black text-left">Description</th>
              <th className="py-1 px-1.5 border-r border-black w-14 text-center">Qty</th>
              <th className="py-1 px-1.5 border-r border-black w-14 text-center">Unit</th>
              <th className="py-1 px-2 border-r border-black w-20 text-right">Rate (₹)</th>
              <th className="py-1 px-2 w-24 text-right">Amount (₹)</th>
            </tr>
          </thead>
          <tbody>
            {effectiveItems.length === 0 ? (
              <tr>
                <td colSpan={6} className="py-4 text-center text-slate-400 italic">
                  No products added to bill
                </td>
              </tr>
            ) : (
              effectiveItems.map((item, idx) => (
                <tr key={item.localId || idx} className="border-b border-slate-200 hover:bg-slate-50">
                  <td className="py-1 px-1.5 border-r border-black text-center font-medium">
                    {idx + 1}
                  </td>
                  <td className="py-1 px-2 border-r border-black font-medium">
                    {item.description}
                  </td>
                  <td className="py-1 px-1.5 border-r border-black text-center">
                    {item.qty}
                  </td>
                  <td className="py-1 px-1.5 border-r border-black text-center">
                    {item.unit || 'Nos'}
                  </td>
                  <td className="py-1 px-2 border-r border-black text-right font-mono">
                    {formatNumberINR(item.rate)}
                  </td>
                  <td className="py-1 px-2 text-right font-bold font-mono">
                    {formatNumberINR(item.amount)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* 5. SUMMARY SECTION */}
      {isQuotation ? (
        // Quotation Summary
        <div className="border border-t-0 border-black flex flex-col sm:flex-row justify-between">
          <div className="flex-1 p-2 sm:p-3 border-b sm:border-b-0 sm:border-r border-black flex flex-col justify-between">
            <div>
              <p className="font-bold text-[9px] text-black">Estimated Amount in Words:</p>
              <p className="text-[10px] font-semibold text-slate-800 italic mt-0.5">
                {convertRupeesToWords(draft.grandTotal)}
              </p>
            </div>
            <div className="mt-3 text-[8.5px] text-slate-600 bg-slate-50 p-2 border border-slate-200 rounded">
              <span className="font-bold text-black block mb-0.5">Note for Customer:</span>
              {draft.quotationNote ||
                'We appreciate the opportunity to serve you. All quoted items use genuine manufacturer-warranted hardware installed by certified technicians of TOTAL SOLUTION.'}
            </div>
          </div>

          <div className="w-full sm:w-64">
            <div className="flex justify-between py-1 px-2 border-b border-slate-300">
              <span className="font-semibold">SUB TOTAL:</span>
              <span className="font-mono font-bold">{formatINR(draft.subtotal)}</span>
            </div>
            <div className="flex justify-between py-1 px-2 border-b border-slate-300 text-rose-600">
              <span className="font-semibold">DISCOUNT:</span>
              <span className="font-mono font-bold">- {formatINR(draft.discount)}</span>
            </div>
            <div className="flex justify-between py-1.5 px-2 bg-slate-200 font-black text-[11px] text-[#1E3A8A]">
              <span>QUOTATION TOTAL:</span>
              <span className="font-mono">{formatINR(draft.grandTotal)}</span>
            </div>
          </div>
        </div>
      ) : (
        // Tax Invoice Summary
        <div className="border border-t-0 border-black flex flex-col sm:flex-row justify-between">
          <div className="flex-1 p-2 sm:p-3 border-b sm:border-b-0 sm:border-r border-black flex flex-col justify-between">
            <div>
              <p className="font-bold text-[9px] text-black">Rupees (Amount in Words):</p>
              <p className="text-[10px] font-semibold text-slate-800 italic mt-0.5">
                {convertRupeesToWords(draft.grandTotal)}
              </p>
              <p className="text-[9.5px] font-bold text-slate-800 mt-2">
                Payment Mode:{' '}
                <span className="font-semibold">
                  {draft.paymentMode === 'ONLINE' ? '● Online (UPI / Card)  ○ Cash' : '○ Online  ● Cash'}
                </span>
              </p>
            </div>

            {/* Online UPI QR & Details */}
            <div className="mt-3 flex items-center gap-3 bg-slate-50 p-2 border border-slate-200 rounded">
              {upiQrUrl ? (
                <img src={upiQrUrl} alt="UPI QR" className="w-16 h-16 object-contain" />
              ) : (
                <div className="w-16 h-16 bg-slate-200 rounded" />
              )}
              <div>
                <span className="font-bold text-[9.5px] text-black block">Online QR Payment :-</span>
                {upi.upiId && (
                  <span className="font-bold text-[9px] text-[#1E3A8A] block">
                    UPI: {upi.upiId}
                  </span>
                )}
                <span className="text-[8.5px] text-slate-700 block">
                  A/c: {upi.payeeName}
                </span>
                <span className="text-[8px] text-emerald-700 font-bold block mt-0.5">
                  Scan with GPay, PhonePe, Paytm or BHIM
                </span>
              </div>
            </div>
          </div>

          <div className="w-full sm:w-64">
            <div className="flex justify-between py-1 px-2 border-b border-slate-300">
              <span className="font-semibold">SUB TOTAL:</span>
              <span className="font-mono font-bold">{formatINR(draft.subtotal)}</span>
            </div>
            {draft.extraChargeAmount > 0 && (
              <div className="flex justify-between py-1 px-2 border-b border-slate-300">
                <span className="font-semibold">EXTRA CHARGE:</span>
                <span className="font-mono font-bold">+{formatINR(draft.extraChargeAmount)}</span>
              </div>
            )}
            {draft.secondaryChargeAmount > 0 && (
              <div className="flex justify-between py-1 px-2 border-b border-slate-300">
                <span className="font-semibold">ADDITIONAL CHARGE:</span>
                <span className="font-mono font-bold">+{formatINR(draft.secondaryChargeAmount)}</span>
              </div>
            )}
            <div className="flex justify-between py-1 px-2 border-b border-slate-300 text-rose-600">
              <span className="font-semibold">DISCOUNT:</span>
              <span className="font-mono font-bold">- {formatINR(draft.discount)}</span>
            </div>
            <div className="flex justify-between py-1.5 px-2 bg-slate-200 font-black text-[11px] text-black">
              <span>GRAND TOTAL:</span>
              <span className="font-mono">{formatINR(draft.grandTotal)}</span>
            </div>
            <div className="flex justify-between py-1 px-2 border-b border-slate-300 text-emerald-700 font-bold">
              <span>PAID AMOUNT:</span>
              <span className="font-mono">{formatINR(draft.paidAmount)}</span>
            </div>
            <div className="flex justify-between py-1 px-2 bg-rose-50 font-black text-rose-700">
              <span>DUE BALANCE:</span>
              <span className="font-mono">{formatINR(draft.dueAmount)}</span>
            </div>
          </div>
        </div>
      )}

      {/* 6. SIGNATURES ROW */}
      <div className="border border-t-0 border-black flex flex-col sm:flex-row justify-between items-end p-3 gap-4">
        <div className="w-full sm:w-1/2 flex flex-col items-start">
          <span className="text-[9px] font-bold text-slate-700 mb-1">Customer Acceptance / Signature:</span>
          {draft.signatureBase64 ? (
            <img src={draft.signatureBase64} alt="Customer Signature" className="h-10 object-contain mb-1" />
          ) : (
            <div className="h-10 w-44 border-b border-dashed border-slate-400 mb-1" />
          )}
          <span className="text-[8.5px] text-slate-500 font-medium">Customer Sign</span>
        </div>

        <div className="w-full sm:w-1/2 flex flex-col items-end text-right">
          {company.authorisedSignatureBase64 ? (
            <img src={company.authorisedSignatureBase64} alt="Authorised Signature" className="h-10 object-contain mb-1" />
          ) : (
            <div className="h-10 w-44 border-b border-dashed border-slate-400 mb-1" />
          )}
          <span className="text-[9px] font-bold text-black">Authorised Signatory</span>
          <span className="text-[8px] font-extrabold text-[#1E3A8A] uppercase">
            {company.companyName}
          </span>
        </div>
      </div>

      {/* 7. PRODUCT DISCLAIMER */}
      <div className="mt-2 border border-black bg-slate-50 p-2 text-center text-[7.5px] text-slate-600 leading-tight">
        <span className="font-bold text-slate-800">Product Disclaimer: </span>
        Product warranty is subject to respective manufacturer terms and conditions. Physical damage, water damage, voltage fluctuation, improper installation or unauthorised tampering are not covered unless manufacturer policy states otherwise.
      </div>
    </div>
  );
};
