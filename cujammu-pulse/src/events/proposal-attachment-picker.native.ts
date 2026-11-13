import * as DocumentPicker from 'expo-document-picker';
import { File, Paths } from 'expo-file-system';

export type SelectedAttachment = { file: Blob; name: string; release(): void };
export async function pickProposalAttachment(): Promise<SelectedAttachment | null> {
  const result = await DocumentPicker.getDocumentAsync({ type: ['image/png', 'image/jpeg', 'image/webp'], multiple: false, copyToCacheDirectory: true });
  if (result.canceled) return null;
  const asset = result.assets[0];
  if (!asset || result.assets.length !== 1 || !asset.uri.startsWith(Paths.cache.uri + (Paths.cache.uri.endsWith('/') ? '' : '/'))) throw new Error('The selected image could not be opened safely.');
  const file = new File(asset.uri);
  const release = () => { if (file.exists) file.delete(); };
  if (file.size < 1 || file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) { release(); throw new Error('Choose one PNG, JPEG or WebP image up to 5 MiB.'); }
  return { file, name: asset.name, release };
}
