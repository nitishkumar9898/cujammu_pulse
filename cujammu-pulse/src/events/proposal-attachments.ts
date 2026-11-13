import type { ProposalMediaKind } from './proposals.ts';

export type ProposalAttachmentReceipt = {
  id: number; revision: number; kind: ProposalMediaKind;
  uploadAcceptedAt: string; expiresAt: string; replayed: boolean;
};

/** Only server-confirmed upload lifetime is displayed; the device clock never assigns expiry. */
export function decodeProposalAttachmentReceipt(raw: unknown): ProposalAttachmentReceipt {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Attachment receipt could not be verified.');
  const value = raw as Record<string, unknown>;
  if (!Number.isSafeInteger(value.id) || (value.id as number) < 1 || !Number.isSafeInteger(value.revision) || (value.revision as number) < 0
      || !['banner', 'certificate-template'].includes(value.kind as string) || typeof value.replayed !== 'boolean')
    throw new Error('Attachment receipt could not be verified.');
  const instant = (input: unknown): string => {
    if (typeof input !== 'string' || input.length > 40 || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(input) || !Number.isFinite(Date.parse(input)))
      throw new Error('Attachment lifetime could not be verified.');
    if (new Date(Date.parse(input)).toISOString().slice(0, 19) !== input.slice(0, 19))
      throw new Error('Attachment lifetime could not be verified.');
    return input;
  };
  const uploadAcceptedAt = instant(value.uploadAcceptedAt); const expiresAt = instant(value.expiresAt);
  if (Date.parse(expiresAt) - Date.parse(uploadAcceptedAt) !== 30 * 24 * 60 * 60 * 1000)
    throw new Error('Attachment lifetime could not be verified.');
  return { id: value.id as number, revision: value.revision as number, kind: value.kind as ProposalMediaKind, uploadAcceptedAt, expiresAt, replayed: value.replayed };
}
