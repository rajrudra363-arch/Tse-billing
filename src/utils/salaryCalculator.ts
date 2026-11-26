import { EmployeeEntity, SalaryPaymentEntity, EmployeeSalarySummary, SalaryPaymentStatus } from '../types';

export function todayIso(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseUtcDate(dateStr: string): Date | null {
  const clean = dateStr.trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(clean)) return null;
  const [yStr, mStr, dStr] = clean.split('-');
  const y = parseInt(yStr, 10);
  const m = parseInt(mStr, 10) - 1;
  const d = parseInt(dStr, 10);
  const dt = new Date(Date.UTC(y, m, d, 0, 0, 0, 0));
  if (isNaN(dt.getTime())) return null;
  return dt;
}

function formatUtcIso(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function daysBetweenInclusive(start: Date, end: Date): number {
  const diffMs = end.getTime() - start.getTime();
  if (diffMs < 0) return 0;
  const days = Math.round(diffMs / 86_400_000);
  return Math.max(0, days + 1);
}

function daysBetweenExclusiveEnd(start: Date, endExclusive: Date): number {
  const diffMs = endExclusive.getTime() - start.getTime();
  if (diffMs <= 0) return 0;
  return Math.max(1, Math.round(diffMs / 86_400_000));
}

function addOneMonthClamped(year: number, monthZero: number, anchorDay: number): Date {
  let nextY = year;
  let nextM = monthZero + 1;
  if (nextM > 11) {
    nextM = 0;
    nextY += 1;
  }
  // Days in next month:
  const daysInNextMonth = new Date(Date.UTC(nextY, nextM + 1, 0)).getUTCDate();
  const clampedDay = Math.min(anchorDay, daysInNextMonth);
  return new Date(Date.UTC(nextY, nextM, clampedDay, 0, 0, 0, 0));
}

function makeClampedDate(year: number, monthZero: number, anchorDay: number): Date {
  const daysInMonth = new Date(Date.UTC(year, monthZero + 1, 0)).getUTCDate();
  const clampedDay = Math.min(anchorDay, daysInMonth);
  return new Date(Date.UTC(year, monthZero, clampedDay, 0, 0, 0, 0));
}

export function resolveSalaryCycle(
  workStartDate: string,
  hiringDate: string,
  referenceDate: string = todayIso()
): [Date, Date] {
  const startCal = parseUtcDate(workStartDate) || parseUtcDate(hiringDate) || parseUtcDate(referenceDate) || new Date();
  const refCal = parseUtcDate(referenceDate) || startCal;
  const anchorDay = startCal.getUTCDate();

  if (refCal.getTime() <= startCal.getTime()) {
    const endEx = addOneMonthClamped(startCal.getUTCFullYear(), startCal.getUTCMonth(), anchorDay);
    return [startCal, endEx];
  }

  const refYear = refCal.getUTCFullYear();
  const refMonth = refCal.getUTCMonth();
  let cycleStart = makeClampedDate(refYear, refMonth, anchorDay);

  if (cycleStart.getTime() > refCal.getTime()) {
    let prevY = refYear;
    let prevM = refMonth - 1;
    if (prevM < 0) {
      prevM = 11;
      prevY -= 1;
    }
    cycleStart = makeClampedDate(prevY, prevM, anchorDay);
  }

  if (cycleStart.getTime() < startCal.getTime()) {
    cycleStart = startCal;
  }

  const cycleEndExclusive = addOneMonthClamped(cycleStart.getUTCFullYear(), cycleStart.getUTCMonth(), anchorDay);
  return [cycleStart, cycleEndExclusive];
}

export function calculateEmployeeSummary(
  employee: EmployeeEntity,
  allPayments: SalaryPaymentEntity[],
  referenceDate: string = todayIso()
): EmployeeSalarySummary {
  const empPayments = allPayments.filter((p) => p.employeeId === employee.employeeId);
  const [cycleStartCal, cycleEndExclusiveCal] = resolveSalaryCycle(
    employee.workStartDate,
    employee.hiringDate,
    referenceDate
  );

  const cycleStartIso = formatUtcIso(cycleStartCal);
  const cycleEndInclusiveCal = new Date(cycleEndExclusiveCal.getTime() - 86_400_000);
  const cycleEndIso = formatUtcIso(cycleEndInclusiveCal);

  const workStartCal = parseUtcDate(employee.workStartDate) || parseUtcDate(employee.hiringDate) || cycleStartCal;
  const workStopCal = employee.workStopDate ? parseUtcDate(employee.workStopDate) : null;
  const refCal = parseUtcDate(referenceDate) || cycleEndInclusiveCal;

  const calendarDaysInCycle = Math.max(1, daysBetweenExclusiveEnd(cycleStartCal, cycleEndExclusiveCal));
  const monthly = Math.max(0, employee.monthlySalary || 0);
  const dailySalary = monthly > 0 ? monthly / calendarDaysInCycle : 0;

  const effectiveStart = workStartCal.getTime() > cycleStartCal.getTime() ? workStartCal : cycleStartCal;
  const stopBoundCandidates = [cycleEndInclusiveCal.getTime(), refCal.getTime()];
  if (workStopCal) stopBoundCandidates.push(workStopCal.getTime());
  const stopBound = Math.min(...stopBoundCandidates);

  const effectiveEnd = new Date(stopBound);
  const eligibleDays =
    effectiveEnd.getTime() >= effectiveStart.getTime()
      ? Math.min(calendarDaysInCycle, daysBetweenInclusive(effectiveStart, effectiveEnd))
      : 0;

  const earnedSalary = Math.round(dailySalary * eligibleDays * 100) / 100;

  const cyclePayments = empPayments.filter((p) => {
    return (
      (!p.cycleStart && p.paymentDate >= cycleStartIso && p.paymentDate <= cycleEndIso) ||
      p.cycleStart === cycleStartIso
    );
  });

  const totalPaidInCycle = cyclePayments.reduce((acc, p) => acc + Math.max(0, p.amount || 0), 0);
  const totalPaidAllTime = empPayments.reduce((acc, p) => acc + Math.max(0, p.amount || 0), 0);
  const remaining = Math.round(Math.max(0, earnedSalary - totalPaidInCycle) * 100) / 100;

  let status: SalaryPaymentStatus;
  if (earnedSalary <= 0.009 && totalPaidInCycle <= 0.009) {
    status = 'PENDING';
  } else if (totalPaidInCycle <= 0.009) {
    status = 'PENDING';
  } else if (remaining <= 0.009) {
    status = 'FULLY_PAID';
  } else {
    status = 'PARTIALLY_PAID';
  }

  return {
    employee,
    cycleStart: cycleStartIso,
    cycleEnd: cycleEndIso,
    calendarDaysInCycle,
    eligibleDays,
    dailySalary,
    earnedSalary,
    totalPaidInCycle,
    totalPaidAllTime,
    remainingBalance: remaining,
    status,
    cyclePayments,
  };
}

export function isDuplicatePayment(
  existingPayments: SalaryPaymentEntity[],
  employeeId: string,
  paymentDate: string,
  amount: number,
  referenceNo: string
): boolean {
  const cleanRef = referenceNo.trim();
  return existingPayments.some((p) => {
    const matchesRef =
      (cleanRef !== '' && p.referenceNo.trim().toLowerCase() === cleanRef.toLowerCase()) ||
      (cleanRef === '' && p.referenceNo.trim() === '');

    return (
      p.employeeId === employeeId &&
      p.paymentDate.trim() === paymentDate.trim() &&
      Math.abs((p.amount || 0) - amount) < 0.01 &&
      matchesRef
    );
  });
}
