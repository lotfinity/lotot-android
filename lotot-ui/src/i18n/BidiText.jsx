import React from 'react';

// Isolate identifiers/measurements that must retain LTR ordering inside Arabic text.
// Keep this conservative: ordinary Darija/French prose remains under the paragraph's base direction.
const TECHNICAL_TOKEN = /(https?:\/\/[^\s]+|(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?|\b[PBCU][0-9A-F]{4}\b|\b(?:OBD(?:-II)?|ECU|ELM327|CAN|ABS|VIN|MQTT|Bluetooth|Wi-?Fi)\b|[-+]?\d+(?:[.,]\d+)?\s*(?:%|V|A|mA|°C|°F|bar|kPa|MPa|psi|rpm|km\/h|mph|Hz|ms|min))/gi;

export function TechnicalToken({ children, className = '' }) {
  return <bdi dir="ltr" className={`technical-token ${className}`.trim()}>{children}</bdi>;
}

export function renderBidiText(value, keyPrefix = 'bidi') {
  const source = String(value ?? '');
  const parts = source.split(TECHNICAL_TOKEN).filter((part) => part !== '');
  return parts.map((part, index) => {
    const key = `${keyPrefix}-${index}`;
    TECHNICAL_TOKEN.lastIndex = 0;
    if (TECHNICAL_TOKEN.test(part)) return <TechnicalToken key={key}>{part}</TechnicalToken>;
    return <React.Fragment key={key}>{part}</React.Fragment>;
  });
}

export function BidiText({ children }) {
  return <>{renderBidiText(children)}</>;
}
