export interface DailyReportRow {
  reportDate: string;
  totalDeliveries: number;
  successfulDeliveries: number;
  failedDeliveries: number;
  successRate?: number;
}
