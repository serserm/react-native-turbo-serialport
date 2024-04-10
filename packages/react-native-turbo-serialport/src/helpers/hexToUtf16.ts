export const hexToUtf16 = (hex: string) => {
  let str = '';
  const radix = 16;
  if (hex) {
    for (let i = 0; i < hex.length && hex.substring(i, 2) !== '00'; i += 2) {
      str += String.fromCharCode(parseInt(hex.substring(i, 2), radix));
    }
  }
  return str;
};
