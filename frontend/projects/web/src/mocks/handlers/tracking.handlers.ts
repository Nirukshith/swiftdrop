import { HttpResponse, http } from 'msw';
import { MockParcelDb } from '../mock-db';
import { readJson, respond } from './respond';

/** A tiny valid one-page PDF, enough for the browser to download and open. */
export function mockReceiptPdf(trackingNumber: string): string {
  const text = `SwiftDrop receipt ${trackingNumber} (mock)`;
  const stream = `BT /F1 14 Tf 72 760 Td (${text}) Tj ET`;
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let pdf = '%PDF-1.4\n';
  const offsets: number[] = [];
  objects.forEach((body, index) => {
    offsets.push(pdf.length);
    pdf += `${index + 1} 0 obj\n${body}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  pdf += offsets.map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return pdf;
}

/** Tracking, history, receipt and rating endpoints from contracts/openapi/parcel-read.yaml (M2). */
export function trackingHandlers(db: MockParcelDb, baseUrl = '') {
  const api = `${baseUrl}/api/v1`;
  return [
    http.get(`${api}/tracking/:trackingNo`, ({ params }) => respond(db.publicTracking(String(params['trackingNo'])))),

    // SSE (week 3): sends the current state once, then ends; EventSource reconnects after `retry`.
    http.get(`${api}/tracking/:trackingNo/stream`, ({ params }) => {
      const result = db.publicTracking(String(params['trackingNo']));
      if (result.status !== 200 || !result.body || !('timeline' in result.body)) {
        return respond(result);
      }
      const parcel = db.findByTrackingNumber(result.body.trackingNumber);
      const event = {
        trackingNumber: result.body.trackingNumber,
        sequence: parcel?.sequence ?? 1,
        status: result.body.status,
        occurredAt: result.body.lastUpdatedAt,
      };
      const frames = `retry: 5000\n\nid: ${event.sequence}\nevent: status\ndata: ${JSON.stringify(event)}\n\n`;
      return new HttpResponse(frames, { headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' } });
    }),

    http.get(`${api}/parcels/:id/history`, ({ params }) => respond(db.parcelHistory(String(params['id'])))),

    http.get(`${api}/parcels/:id/receipt`, ({ params }) => {
      const found = db.getParcel(String(params['id']));
      if (found.status !== 200 || !found.body || !('trackingNumber' in found.body)) {
        return respond(found);
      }
      const trackingNumber = found.body.trackingNumber;
      return new HttpResponse(mockReceiptPdf(trackingNumber), {
        headers: {
          'Content-Type': 'application/pdf',
          'Content-Disposition': `attachment; filename="receipt-${trackingNumber}.pdf"`,
        },
      });
    }),

    http.post(`${api}/parcels/:id/rating`, async ({ params, request }) => respond(db.rateParcel(String(params['id']), await readJson(request)))),
    http.get(`${api}/parcels/:id/rating`, ({ params }) => respond(db.getRating(String(params['id'])))),
  ];
}
