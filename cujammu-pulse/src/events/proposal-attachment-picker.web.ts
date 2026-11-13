import * as DocumentPicker from 'expo-document-picker';

export type SelectedAttachment = { file: Blob; name: string; release(): void };
export async function pickProposalAttachment(): Promise<SelectedAttachment | null> {
  const result = await DocumentPicker.getDocumentAsync({ type: ['image/png', 'image/jpeg', 'image/webp'], multiple: false, base64: false });
  if (result.canceled) return null;
  const asset = result.assets[0]; const file = asset?.file;
  if (!file || result.assets.length !== 1 || file.size < 1 || file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) throw new Error('Choose one PNG, JPEG or WebP image up to 5 MiB.');
  return { file, name: asset.name, release() {} };
}
