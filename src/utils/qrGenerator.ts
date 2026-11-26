import QRCode from 'qrcode';
import { AppIdentity } from '../types';

export const CUSTOMER_SUPPORT_WHATSAPP_MESSAGE =
  'Hello! I need support from TOTAL SOLUTION. Please assist me. Thank you! 😊';

export function buildUpiUri(upiId: string, payeeName: string, amount: number, invoiceNo: string): string {
  const pa = upiId.trim();
  const pn = (payeeName.trim() || AppIdentity.OFFICIAL_COMPANY_NAME);
  let uri = `upi://pay?pa=${encodeURIComponent(pa)}&pn=${encodeURIComponent(pn)}&cu=INR`;
  if (amount > 0.009) {
    uri += `&am=${amount.toFixed(2)}`;
  }
  if (invoiceNo.trim()) {
    uri += `&tn=${encodeURIComponent(`Invoice ${invoiceNo.trim()}`)}`;
  }
  return uri;
}

export function buildWhatsAppSupportUri(mobileRaw: string): string {
  const digitsOnly = mobileRaw.replace(/\D/g, '');
  if (digitsOnly.length < 7) return '';
  const formatted = digitsOnly.length === 10 ? `91${digitsOnly}` : digitsOnly;
  return `https://wa.me/${formatted}?text=${encodeURIComponent(CUSTOMER_SUPPORT_WHATSAPP_MESSAGE)}`;
}

export function buildTechnicianIntroWhatsAppUri(customerMobileRaw: string, technicianName: string): string {
  const digitsOnly = customerMobileRaw.replace(/\D/g, '');
  if (digitsOnly.length < 7) return '';
  const formatted = digitsOnly.length === 10 ? `91${digitsOnly}` : digitsOnly;
  const safeTechName = technicianName.trim() || 'Technician';
  const text = `Hello! My name is ${safeTechName}. I am the Technician assigned by ${AppIdentity.OFFICIAL_COMPANY_NAME} for your service request. I am contacting you regarding your service. Thank you!`;
  return `https://wa.me/${formatted}?text=${encodeURIComponent(text)}`;
}

export async function generateQrDataUrl(content: string, size: number = 300): Promise<string> {
  const trimmed = content.trim();
  if (!trimmed) return '';
  try {
    return await QRCode.toDataURL(trimmed, {
      width: size,
      margin: 1,
      color: {
        dark: '#000000',
        light: '#ffffff',
      },
      errorCorrectionLevel: 'M',
    });
  } catch (err) {
    console.error('Failed to generate QR code:', err);
    return '';
  }
}
