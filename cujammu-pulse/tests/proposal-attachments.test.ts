import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeProposalAttachmentReceipt } from '../src/events/proposal-attachments.ts';

const receipt = { id: 7, revision: 9, kind: 'banner', uploadAcceptedAt: '2035-04-01T10:00:00Z', expiresAt: '2035-05-01T10:00:00Z', replayed: false };
test('attachment receipt projects server lifetime and drops private storage fields', () => {
  assert.deepEqual(decodeProposalAttachmentReceipt({ ...receipt, objectKey: 'private-key', owner: { email: 'private@example.invalid' } }), receipt);
  assert.equal(decodeProposalAttachmentReceipt({ ...receipt, replayed: true }).expiresAt, receipt.expiresAt);
});
test('attachment receipt rejects invalid identifiers, kind and extended or ambiguous expiry', () => {
  for (const patch of [{ id: 0 }, { revision: 1.5 }, { kind: 'avatar' }, { replayed: 'true' }, { expiresAt: '2035-05-02T10:00:00Z' }, { uploadAcceptedAt: '2035-04-01T10:00:00' }])
    assert.throws(() => decodeProposalAttachmentReceipt({ ...receipt, ...patch }));
  assert.throws(() => decodeProposalAttachmentReceipt({ ...receipt, uploadAcceptedAt: '2035-02-30T10:00:00Z', expiresAt: '2035-04-01T10:00:00Z' }));
});
