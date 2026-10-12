export function describesDirectPushException(text) {
  const directPush = String.raw`direct[\s-]+push(?:ing)?`;
  const exceptionBeforePush = String.raw`exception(?:\s+\w+){0,6}\s+${directPush}`;
  const pushBeforeException = String.raw`${directPush}(?:\s+\w+){0,6}\s+exception`;
  const exceptionAndPush = new RegExp(`(?:${exceptionBeforePush}|${pushBeforeException})`, "iu");

  return text
    .split(/[.!?](?:\s+|$)|\n{2,}/u)
    .some(sentence => /Development1/iu.test(sentence) && exceptionAndPush.test(sentence));
}
