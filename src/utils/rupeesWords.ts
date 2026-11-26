// Indian Rupee Amount to Words Converter (faithful to RupeesWords.kt)

const ones = [
  '', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine',
  'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen',
  'Seventeen', 'Eighteen', 'Nineteen'
];

const tens = [
  '', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'
];

function twoDigits(n: number): string {
  if (n < 20) return ones[n];
  const t = tens[Math.floor(n / 10)];
  const o = n % 10 !== 0 ? ` ${ones[n % 10]}` : '';
  return `${t}${o}`;
}

function convertWhole(n: number): string {
  if (n <= 0) return '';
  let parts: string[] = [];
  let rem = Math.floor(n);

  if (rem >= 10_000_000) {
    const crore = Math.floor(rem / 10_000_000);
    parts.push(`${convertWhole(crore)} Crore`);
    rem %= 10_000_000;
  }
  if (rem >= 100_000) {
    const lakh = Math.floor(rem / 100_000);
    parts.push(`${twoDigits(lakh)} Lakh`);
    rem %= 100_000;
  }
  if (rem >= 1_000) {
    const thousand = Math.floor(rem / 1_000);
    parts.push(`${twoDigits(thousand)} Thousand`);
    rem %= 1_000;
  }
  if (rem >= 100) {
    const hundred = Math.floor(rem / 100);
    parts.push(`${ones[hundred]} Hundred`);
    rem %= 100;
  }
  if (rem > 0) {
    parts.push(twoDigits(rem));
  }
  return parts.join(' ').trim();
}

export function convertRupeesToWords(amount: number): string {
  const safe = Math.max(0, amount);
  const whole = Math.floor(safe);
  const paise = Math.round((safe - whole) * 100);

  const wholeWords = convertWhole(whole) || 'Zero';
  const paiseWords = paise > 0 ? ` and ${twoDigits(paise)} Paise` : '';

  return `${wholeWords}${paiseWords} Only`;
}

export function formatINR(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount || 0);
}

export function formatNumberINR(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount || 0);
}
