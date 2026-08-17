import React, { createContext, useContext } from 'react';

export const DEFAULT_ICON_FAMILY = 'industrial-soft';
export const ICON_FAMILY_IDS = Object.freeze(['tech-line', 'industrial-soft', 'neo-ecu']);

const IconFamilyContext = createContext(DEFAULT_ICON_FAMILY);

export function normalizeIconFamily(family) {
  return ICON_FAMILY_IDS.includes(family) ? family : DEFAULT_ICON_FAMILY;
}

export function IconFamilyProvider({ family, children }) {
  return (
    <IconFamilyContext.Provider value={normalizeIconFamily(family)}>
      {children}
    </IconFamilyContext.Provider>
  );
}

export function useIconFamily() {
  return useContext(IconFamilyContext);
}
