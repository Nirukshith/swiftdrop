export const CSV_HEADER = 'externalReference,recipientName,recipientPhone,pickupLine1,pickupLine2,pickupCity,pickupDistrict,pickupPostalCode,dropoffLine1,dropoffLine2,dropoffCity,dropoffDistrict,dropoffPostalCode,size,weightKg';
export const MAX_CSV_BYTES = 5 * 1024 * 1024;
const DISTRICTS = ['Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo', 'Galle', 'Gampaha', 'Hambantota', 'Jaffna', 'Kalutara', 'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala', 'Mannar', 'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya', 'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya'];
export interface PreviewRow { rowNumber: number; cells: string[]; errors: string[]; }

export function parseMerchantCsv(input: string): PreviewRow[] {
  const text = input.replace(/^\uFEFF/, '');
  const records: PreviewRow[] = [];
  let cells: string[] = [], field = '', quoted = false, closed = false, line = 1, start = 1;
  for (let i = 0; i <= text.length; i++) {
    const end = i === text.length, ch = end ? '\n' : text[i];
    if (ch === '\0') throw new Error('CSV contains a NUL character.');
    if (quoted) {
      if (end) throw new Error('CSV contains an unclosed quoted field.');
      if (ch === '"') {
        if (text[i + 1] === '"') { field += '"'; i++; } else { quoted = false; closed = true; }
      } else { field += ch; if (ch === '\n' || (ch === '\r' && text[i + 1] !== '\n')) line++; }
    } else if (ch === '"') {
      if (field || closed) throw new Error('Unexpected quote in CSV field.');
      quoted = true;
    } else if ([',', '\n', '\r'].includes(ch)) {
      cells.push(field); field = ''; closed = false;
      if (cells.length > 15) throw new Error('CSV contains too many columns.');
      if (ch !== ',') {
        if (cells.length !== 1 || cells[0].trim()) records.push({ rowNumber: start, cells, errors: [] });
        cells = [];
        if (records.length > 101) throw new Error('CSV exceeds 100 data rows.');
        if (ch === '\r' && text[i + 1] === '\n') i++;
        line++; start = line;
      }
    } else {
      if (closed) throw new Error('Unexpected character after CSV closing quote.');
      field += ch;
    }
  }
  if (JSON.stringify(records.shift()?.cells) !== JSON.stringify(CSV_HEADER.split(','))) throw new Error('CSV header must exactly match the template.');
  if (!records.length) throw new Error('CSV must contain at least one data row.');
  const counts = new Map<string, number>();
  for (const row of records) {
    row.cells = row.cells.map(c => c.trim());
    const c = row.cells, errors = row.errors;
    if (c.length !== 15) { errors.push('Expected 15 columns.'); continue; }
    for (const index of [0, 1, 2, 3, 5, 6, 7, 8, 10, 11, 12, 13, 14]) {
      if (!c[index]) errors.push(`${CSV_HEADER.split(',')[index]} is required.`);
    }
    if (c[0].length > 128) errors.push('externalReference exceeds 128 characters.');
    if (c.slice(1, 13).some(v => v.length > 255)) errors.push('Address/contact columns must not exceed 255 characters.');
    if (!['SMALL', 'MEDIUM', 'LARGE'].includes(c[13])) errors.push('size must be SMALL, MEDIUM or LARGE.');
    const decimal = c[14].match(/^[+-]?((?:\d+(?:\.\d*)?|\.\d+))(?:[eE]([+-]?\d+))?$/);
    const precision = decimal?.[1].replace('.', '').replace(/^0+/, '').length || 1;
    const scale = (decimal?.[1].split('.')[1]?.length || 0) - Number(decimal?.[2] || 0);
    if (!decimal || !Number.isFinite(Number(c[14])) || Number(c[14]) < 0.1 || precision > 12 || Math.abs(scale) > 6) errors.push('weightKg must be at least 0.10 with at most 12 digits and 6 decimal places.');
    for (const index of [6, 11]) {
      const district = DISTRICTS.find(d => d.toLowerCase() === c[index].replaceAll('_', ' ').toLowerCase());
      if (!district) errors.push(`${index === 6 ? 'pickup' : 'dropoff'}District must be a supported Sri Lankan district.`);
      else c[index] = district;
    }
    counts.set(c[0], (counts.get(c[0]) || 0) + 1);
  }
  for (const row of records) if (row.cells.length === 15 && (counts.get(row.cells[0]) || 0) > 1) row.errors.push('Duplicate externalReference in this file.');
  return records;
}

export function encodeMerchantCsv(rows: PreviewRow[]): { csv: string; sourceRows: Record<number, number> } {
  let csv = CSV_HEADER + '\n', line = 2;
  const sourceRows: Record<number, number> = {};
  for (const row of rows) {
    const record = row.cells.map(cell => /[",\r\n]/.test(cell) ? '"' + cell.replaceAll('"', '""') + '"' : cell).join(',');
    sourceRows[line] = row.rowNumber;
    csv += record + '\n';
    line += (record.match(/\r\n|\r|\n/g)?.length || 0) + 1;
  }
  return { csv, sourceRows };
}
