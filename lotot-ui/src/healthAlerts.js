/** Avoid presenting MIL and its associated reported DTC count as two different faults. */
export function collapseDuplicateMilAlert(issues) {
  if (!issues.some((issue) => issue.signal?.mnemonic === 'number_fault_codes')) return issues;
  return issues.filter((issue) => issue.signal?.mnemonic !== 'status_mil');
}
