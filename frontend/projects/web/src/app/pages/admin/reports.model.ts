export interface DailyReportRow {
  reportDate: string;
  totalDeliveries: number;
  successfulDeliveries: number;
  failedDeliveries: number;
  successRate?: number;
}

export interface ZoneReportSummary {
  zoneName: string;
  districts: string[];
  served: boolean;
  totalDeliveries: number;
  successfulDeliveries: number;
  failedDeliveries: number;
  successRate: number;
}

