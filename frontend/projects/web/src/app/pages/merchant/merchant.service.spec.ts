import { firstValueFrom } from 'rxjs';
import { MerchantService } from './merchant.service';

describe('MerchantService', () => {
  it('returns only orders owned by the current merchant', async () => {
    const service = new MerchantService();
    const orders = await firstValueFrom(service.getOrders());
    const counts = await firstValueFrom(service.getOrderCounts());

    expect(orders).toHaveLength(6);
    expect(orders.every((order) => order.merchantId === '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d')).toBe(true);
    expect(counts.total).toBe(6);
  });
});
