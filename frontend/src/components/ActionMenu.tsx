import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'

/** One item in an actions menu. */
export type MenuAction = {
  label: string
  /** A fuller name for screen readers, e.g. one that names the job, so the cards can be told apart. */
  accessibleLabel?: string
  onSelect: () => void
  disabled?: boolean
}

type Props = {
  /** The menu button's accessible name, e.g. "More actions for Staff Engineer". */
  label: string
  actions: MenuAction[]
}

/** A ⋮ button that opens a short list of actions. Renders nothing when there are none. */
export function ActionMenu({ label, actions }: Props) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLDivElement>(null)
  const menuId = useId()

  const items = () => Array.from(menu.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]:not(:disabled)') ?? [])

  // Opening puts the user on the first action, as a menu button should.
  useEffect(() => {
    if (open) items()[0]?.focus()
  }, [open])

  // A click anywhere else closes the menu, leaving focus where the click put it.
  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  if (actions.length === 0) return null

  const close = () => {
    setOpen(false)
    button.current?.focus()
  }

  const onMenuKeyDown = (event: KeyboardEvent) => {
    const all = items()
    const at = all.indexOf(document.activeElement as HTMLButtonElement)
    const focusAt = (index: number) => all[(index + all.length) % all.length]?.focus()
    if (event.key === 'Escape') {
      event.preventDefault()
      close()
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      focusAt(at + 1)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      focusAt(at - 1)
    } else if (event.key === 'Home') {
      event.preventDefault()
      focusAt(0)
    } else if (event.key === 'End') {
      event.preventDefault()
      focusAt(all.length - 1)
    } else if (event.key === 'Tab') {
      setOpen(false)
    }
  }

  return (
    <div ref={root} className="relative">
      <button
        ref={button}
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => setOpen((was) => !was)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' && !open) {
            event.preventDefault()
            setOpen(true)
          }
        }}
        className="-mr-1.5 -mt-1 rounded-md p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-200"
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5">
          <circle cx="10" cy="4" r="1.6" />
          <circle cx="10" cy="10" r="1.6" />
          <circle cx="10" cy="16" r="1.6" />
        </svg>
      </button>
      {open && (
        <div
          ref={menu}
          id={menuId}
          role="menu"
          aria-label={label}
          onKeyDown={onMenuKeyDown}
          className="absolute right-0 top-full z-20 mt-1 w-48 rounded-md border border-slate-200 bg-white py-1 shadow-lg"
        >
          {actions.map((action) => (
            <button
              key={action.label}
              type="button"
              role="menuitem"
              tabIndex={-1}
              disabled={action.disabled}
              aria-label={action.accessibleLabel}
              onClick={() => {
                // Back on the menu button first, so anything the action opens can take focus after it.
                close()
                action.onSelect()
              }}
              className="block w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50 focus:bg-indigo-50 focus:text-indigo-800 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
            >
              {action.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
