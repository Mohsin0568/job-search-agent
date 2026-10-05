import { useCallback, useState } from 'react'
import { useProfile } from '../api/hooks'
import { Alert } from '../components/form/Alert'
import { ProfileForm } from '../profile/ProfileForm'

export function ProfilePage() {
  // RequireProfile has already loaded the profile, so this reads from the cache.
  const { data: profile } = useProfile()
  const [saved, setSaved] = useState(false)
  const hideSavedMessage = useCallback(() => setSaved(false), [])

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold">Search preferences</h1>
      <p className="mt-2 text-slate-500">Changes apply from the next daily job search.</p>

      <div className="mt-8 space-y-5 rounded-xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
        {saved && <Alert variant="success">Your preferences have been saved.</Alert>}
        <ProfileForm
          initialProfile={profile}
          submitLabel="Save changes"
          autoFocus={false}
          onSaved={() => {
            setSaved(true)
            window.scrollTo({ top: 0, behavior: 'smooth' })
          }}
          onChange={hideSavedMessage}
        />
      </div>
    </div>
  )
}
