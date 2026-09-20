/* Preset avatars, drawn as inline SVG so there are no image requests. */

export const AVATAR_COLORS = [
  '#c0a578',
  '#8d9a7e',
  '#7e91a5',
  '#b08268',
  '#9a8098',
  '#a8a06c',
  '#a97b6b',
  '#78909a',
]

export const AVATAR_COUNT = AVATAR_COLORS.length

export default function Avatar({ id = 0, size = 32, ring = false }) {
  const color = AVATAR_COLORS[id % AVATAR_COLORS.length]
  const face = id % 8

  return (
      <svg
          width={size}
          height={size}
          viewBox="0 0 40 40"
          style={{
            borderRadius: '50%',
            display: 'block',
            background: 'var(--bg-soft)',
            boxShadow: ring ? `0 0 0 2px ${color}` : 'none',
          }}
      >
        <Body face={face} color={color} />

        <Face face={face} />
      </svg>
  )
}

function Body({ face, color }) {
  if (face === 3 || face === 6) {
    return <rect x="8" y="9" width="24" height="24" rx="7" fill={color} />
  }
  if (face === 1 || face === 5) {
    return (
        <g fill={color}>
          <rect x="19" y="4" width="2" height="6" rx="1" />
          <circle cx="20" cy="4" r="2" />
          <circle cx="20" cy="22" r="12" />
        </g>
    )
  }
  if (face === 4) {
    return (
        <g fill={color}>
          <path d="M11 12 L13 5 L17 11 Z" />
          <path d="M29 12 L27 5 L23 11 Z" />
          <circle cx="20" cy="22" r="12" />
        </g>
    )
  }

  return <circle cx="20" cy="20" r="13" fill={color} />
}

function Face({ face }) {
  const eye = 'var(--bg)'

  switch (face) {
    case 0:
      return <circle cx="20" cy="19" r="5" fill={eye} />

    case 1:
      return (
          <g fill={eye}>
            <circle cx="16" cy="21" r="2.4" />
            <circle cx="24" cy="21" r="2.4" />
            <path d="M15 27 Q20 30 25 27" stroke={eye} strokeWidth="1.6" fill="none" strokeLinecap="round" />
          </g>
      )

    case 2:
      return (
          <g fill={eye}>
            <circle cx="14" cy="21" r="2.2" />
            <circle cx="20" cy="17" r="2.2" />
            <circle cx="26" cy="21" r="2.2" />
          </g>
      )

    case 3:
      return (
          <g fill={eye}>
            <rect x="13" y="17" width="4" height="4" rx="1" />
            <rect x="23" y="17" width="4" height="4" rx="1" />
            <path d="M15 26 L18 29 L21 26 L24 29 L27 26 Z" />
          </g>
      )

    case 4:
      return (
          <g fill={eye}>
            <circle cx="16" cy="21" r="3.4" />
            <circle cx="25" cy="22" r="1.8" />
          </g>
      )

    case 5:
      return (
          <g stroke={eye} strokeWidth="1.8" strokeLinecap="round">
            <path d="M14 21 L18 21" />
            <path d="M22 21 L26 21" />
            <path d="M18 27 L22 27" />
          </g>
      )

    case 6:
      return (
          <g fill={eye}>
            <rect x="12" y="19" width="16" height="3" rx="1.5" />
            <circle cx="20" cy="27" r="1.6" />
          </g>
      )

    default:
      return (
          <g fill={eye}>
            <circle cx="15" cy="17" r="2" />
            <circle cx="25" cy="17" r="2" />
            <ellipse cx="20" cy="26" rx="6" ry="3.5" />
          </g>
      )
  }
}
