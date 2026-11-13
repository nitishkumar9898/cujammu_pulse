import React, { useMemo } from 'react';
import QRCode from 'qrcode';
import Svg, { Path, Rect } from 'react-native-svg';

/** Render an opaque, short-lived server credential. Never persist or log this value. */
export function AttendanceQr({ value, label }: { value: string; label: string }) {
  const qr = useMemo(() => {
    if (!value || value.length > 512) throw new Error('Invalid attendance credential');
    const matrix = QRCode.create(value, { errorCorrectionLevel: 'M' }).modules;
    let path = '';
    for (let y = 0; y < matrix.size; y++) {
      for (let x = 0; x < matrix.size; x++) {
        if (matrix.data[y * matrix.size + x]) path += `M${x} ${y}h1v1h-1z`;
      }
    }
    return { size: matrix.size, path };
  }, [value]);
  const quiet = 4;
  return <Svg width={256} height={256} viewBox={`${-quiet} ${-quiet} ${qr.size + quiet * 2} ${qr.size + quiet * 2}`} accessibilityLabel={label} role="img">
    <Rect x={-quiet} y={-quiet} width={qr.size + quiet * 2} height={qr.size + quiet * 2} fill="#ffffff" />
    <Path d={qr.path} fill="#111827" />
  </Svg>;
}
