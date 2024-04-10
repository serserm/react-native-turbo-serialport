export const intArrayToUtf16 = (intArray: Array<number>) => {
  let str = '';
  if (Array.isArray(intArray)) {
    for (let i = 0; i < intArray.length; i++) {
      str += String.fromCharCode(intArray[i] as number);
    }
  }
  return str;
};
