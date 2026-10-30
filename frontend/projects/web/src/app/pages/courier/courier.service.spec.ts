import { CourierService } from './courier.service';

describe('CourierService', () => {
  let service: CourierService;

  beforeEach(() => {
    service = new CourierService();
  });

  it('should be created and have initial deliveries', () => {
    expect(service).toBeTruthy();
    expect(service.deliveries().length).toBeGreaterThan(0);
  });

  it('should calculate courier stats accurately', () => {
    const stats = service.stats();
    expect(stats.total).toBe(service.deliveries().length);
    expect(stats.total).toBe(
      stats.assigned + stats.pickedUp + stats.inTransit + stats.delivered + stats.failed
    );
  });

  it('should allow valid status transitions', (done) => {
    // pcl-55003 is currently ASSIGNED
    service.updateStatus('pcl-55003', { status: 'PICKED_UP' }).subscribe((updated) => {
      expect(updated.status).toBe('PICKED_UP');
      expect(service.getDeliveryById('pcl-55003')?.status).toBe('PICKED_UP');

      // Now move PICKED_UP -> IN_TRANSIT
      service.updateStatus('pcl-55003', { status: 'IN_TRANSIT' }).subscribe((inTransit) => {
        expect(inTransit.status).toBe('IN_TRANSIT');
        done();
      });
    });
  });

  it('should handle idempotent retries without error', (done) => {
    // pcl-55001 is already IN_TRANSIT
    service.updateStatus('pcl-55001', { status: 'IN_TRANSIT' }).subscribe((res) => {
      expect(res.status).toBe('IN_TRANSIT');
      done();
    });
  });

  it('should reject invalid status transitions', () => {
    // pcl-55003 is ASSIGNED; cannot jump directly to DELIVERED
    expect(() => {
      service.updateStatus('pcl-55003', { status: 'DELIVERED' });
    }).toThrow(/Cannot transition delivery/);
  });

  it('should upload delivery proof and transition status to DELIVERED', (done) => {
    // pcl-55001 is IN_TRANSIT
    const dummyBlob = new Blob(['sample-proof-data'], { type: 'image/jpeg' });
    const dummyFile = new File([dummyBlob], 'proof.jpg', { type: 'image/jpeg' });

    service
      .uploadProof('pcl-55001', {
        file: dummyFile,
        recipientName: 'Sunil Weerasinghe',
        deliveryNotes: 'Left with security guard',
      })
      .subscribe((updated) => {
        expect(updated.status).toBe('DELIVERED');
        expect(updated.proof).toBeTruthy();
        expect(updated.proof?.fileName).toBe('proof.jpg');
        expect(updated.proof?.recipientName).toBe('Sunil Weerasinghe');
        done();
      });
  });

  it('should reject proof upload if file exceeds 5MB', () => {
    // create fake file > 5MB
    const largeBlob = { size: 6 * 1024 * 1024, name: 'large.jpg', type: 'image/jpeg' } as File;
    expect(() => {
      service.uploadProof('pcl-55001', {
        file: largeBlob,
        recipientName: 'Sunil',
      });
    }).toThrow(/File exceeds 5MB size limit/);
  });

  it('should support marking delivery failed with a reason', (done) => {
    // pcl-55001 is IN_TRANSIT
    service
      .updateStatus('pcl-55001', {
        status: 'FAILED_DELIVERY',
        reason: 'Customer premises closed',
      })
      .subscribe((updated) => {
        expect(updated.status).toBe('FAILED_DELIVERY');
        expect(updated.failureReason).toBe('Customer premises closed');
        done();
      });
  });

  it('should produce a contract-shaped daily summary', () => {
    const summary = service.getDailySummary('2026-10-28');
    expect(summary.date).toBe('2026-10-28');
    expect(summary.totalDeliveries).toBe(summary.deliveries.length);
    expect(summary.pendingCount + summary.completedCount).toBe(summary.totalDeliveries);
    expect(summary.deliveries[0]).toEqual(expect.objectContaining({
      parcelId: expect.any(String),
      trackingNumber: expect.any(String),
      dropoffAddress: expect.any(String),
      attemptCount: expect.any(Number),
      sequenceOrder: expect.any(Number),
    }));
  });

  it('should toggle courier availability', () => {
    const initial = service.profile().availability;
    service.toggleAvailability();
    expect(service.profile().availability).toBe(!initial);
  });
});
