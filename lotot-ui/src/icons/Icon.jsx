import { getIconPath, getIconProfile, shouldMirrorIconInRtl } from './iconRegistry.jsx';
import { normalizeIconFamily, useIconFamily } from './IconFamilyContext.jsx';

export default function Icon({ name, size, className, strokeWidth, filled = false, family, ...rest }) {
  const inheritedFamily = useIconFamily();
  const resolvedFamily = normalizeIconFamily(family || inheritedFamily);
  const profile = getIconProfile(resolvedFamily);
  const resolvedStrokeWidth = strokeWidth ?? profile.strokeWidth;
  const mirrorInRtl = shouldMirrorIconInRtl(name);

  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      className={className}
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={resolvedStrokeWidth}
      strokeLinecap={profile.strokeLinecap}
      strokeLinejoin={profile.strokeLinejoin}
      aria-hidden="true"
      data-icon-family={resolvedFamily}
      data-mirror-in-rtl={mirrorInRtl ? 'true' : undefined}
      {...rest}
    >
      {getIconPath(name, resolvedFamily)}
    </svg>
  );
}
