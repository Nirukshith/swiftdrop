import { CSV_HEADER, encodeMerchantCsv, parseMerchantCsv } from './merchant-csv';

export const validRow = (ref = 'ref-1') => `${ref},Asha,+94771234567,10 Main Road,,Colombo,Colombo,00100,20 Lake Road,,Gampaha,Gampaha,11000,SMALL,1.5`;
export const csvFile = (rows: string) => CSV_HEADER + '\n' + rows + '\n';

describe('Merchant CSV preview', () => {
  it('parses BOM, CRLF, quoted commas, escaped quotes and embedded newlines', () => {
    const text = '\uFEFF' + CSV_HEADER + '\r\n' + validRow().replace('Asha', '"Asha, ""Shop""\nColombo"') + '\r\n' + validRow('next');
    const rows = parseMerchantCsv(text);
    expect(rows).toHaveLength(2);
    expect(rows[0].cells[1]).toBe('Asha, "Shop"\nColombo');
    expect(rows[0].errors).toEqual([]);
    expect(rows[1].rowNumber).toBe(4);
    const encoded = encodeMerchantCsv(rows);
    expect(parseMerchantCsv(encoded.csv).map(r => r.cells)).toEqual(rows.map(r => r.cells));
    expect(encoded.sourceRows).toEqual({ 2: 2, 4: 4 });
  });
  it.each(['', 'wrong,header\nabc', `"${CSV_HEADER}"\nabc`, CSV_HEADER, csvFile('"unclosed'), csvFile('abc"bad'), csvFile('"abc"bad'), csvFile('abc\0')])('rejects malformed CSV %s', text => {
    expect(() => parseMerchantCsv(text)).toThrow();
  });
  it('marks both duplicate references and keeps a different valid row', () => {
    const rows = parseMerchantCsv(csvFile(validRow('same') + '\n' + validRow('same') + '\n' + validRow('good')));
    expect(rows[0].errors.join()).toContain('Duplicate'); expect(rows[1].errors.join()).toContain('Duplicate');
    expect(rows[2].errors).toEqual([]);
  });
  it.each(['0.01', '-1', 'NaN', 'Infinity', '0x10', '1.0000001', '1e100', '1234567890123'])('rejects invalid weight %s', weight => {
    expect(parseMerchantCsv(csvFile(validRow().replace('SMALL,1.5', `SMALL,${weight}`)))[0].errors.join()).toContain('weightKg');
  });
  it('reports missing fields, invalid district, size and incorrect column count', () => {
    const rows = parseMerchantCsv(csvFile(validRow().replace('Colombo,00100', 'Unknown,').replace('SMALL,1.5', 'HUGE,1.5') + '\nshort,row'));
    expect(rows[0].errors.join()).toContain('pickupDistrict');
    expect(rows[0].errors.join()).toContain('pickupPostalCode');
    expect(rows[0].errors.join()).toContain('size');
    expect(rows[1].errors.join()).toContain('15 columns');
  });
  it('enforces 100 rows and preserves source row numbers after removing invalid rows', () => {
    expect(parseMerchantCsv(csvFile(Array.from({ length: 100 }, (_, i) => validRow('ref-' + i)).join('\n')))).toHaveLength(100);
    expect(() => parseMerchantCsv(csvFile((validRow() + '\n').repeat(101)))).toThrow('100');
    const rows = parseMerchantCsv(csvFile('short,row\n' + validRow()));
    expect(encodeMerchantCsv(rows.filter(row => !row.errors.length)).sourceRows).toEqual({ 2: 3 });
  });
});
