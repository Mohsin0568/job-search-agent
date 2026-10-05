import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useId, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useSaveProfile } from '../api/hooks'
import { MAX_COMPANY_PREFERENCES, MAX_SKILLS, type CandidateProfile } from '../api/types'
import { Alert } from '../components/form/Alert'
import { inputClass, labelClass } from '../components/form/fieldStyles'
import { SubmitButton } from '../components/form/SubmitButton'
import { TextAreaField } from '../components/form/TextAreaField'
import { TextField } from '../components/form/TextField'
import { SuggestTagInput } from './SuggestTagInput'
import { profileSchema, RECENCY_OPTIONS, toFormValues, toProfile, type ProfileFormValues } from './profileSchema'

type Props = {
  initialProfile?: CandidateProfile | null
  submitLabel: string
  autoFocus?: boolean
  onSaved?: (profile: CandidateProfile) => void
  /** Called when the user edits any field, e.g. to hide a "saved" message. */
  onChange?: () => void
}

export function ProfileForm({ initialProfile, submitLabel, autoFocus = true, onSaved, onChange }: Props) {
  const recencyId = useId()
  const saveProfile = useSaveProfile()
  const [formError, setFormError] = useState<string>()

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: toFormValues(initialProfile),
  })

  // Fires only when a value actually changes (including TagInput chips, which raise no native
  // form event) - not on submit-state updates, so a "saved" message survives the save itself.
  const valuesKey = JSON.stringify(useWatch({ control }))
  useEffect(() => {
    onChange?.()
  }, [valuesKey, onChange])

  const onSubmit = async (values: ProfileFormValues) => {
    setFormError(undefined)
    try {
      const saved = await saveProfile.mutateAsync(toProfile(values))
      onSaved?.(saved)
    } catch (error) {
      // 400s carry the backend's validation message; anything else is unexpected.
      setFormError(error instanceof Error ? error.message : 'Couldn’t save your profile. Please try again.')
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
      {formError && <Alert variant="error">{formError}</Alert>}

      <TextField
        label="Role you’re looking for"
        placeholder="e.g. Senior Java Developer"
        autoFocus={autoFocus}
        error={errors.desiredRole?.message}
        {...register('desiredRole')}
      />

      <Controller
        control={control}
        name="companyPreferences"
        render={({ field, fieldState }) => (
          <SuggestTagInput
            source="companies"
            label="Companies to search"
            value={field.value}
            onChange={field.onChange}
            onBlur={field.onBlur}
            placeholder="Start typing, e.g. Deliveroo"
            maxItems={MAX_COMPANY_PREFERENCES}
            maxItemLength={200}
            error={fieldState.error?.message}
            hint="Pick from the suggestions to get the name right, or press Enter to add a company that isn’t listed."
          />
        )}
      />

      <Controller
        control={control}
        name="skills"
        render={({ field, fieldState }) => (
          <SuggestTagInput
            source="skills"
            label="Key skills"
            value={field.value}
            onChange={field.onChange}
            onBlur={field.onBlur}
            placeholder="Start typing, e.g. Spring Boot"
            maxItems={MAX_SKILLS}
            maxItemLength={100}
            error={fieldState.error?.message}
            hint="Used to score how well each job matches you. Pick from the suggestions, or press Enter to add your own."
          />
        )}
      />

      <TextAreaField
        label="Current role (optional)"
        placeholder="Paste or summarise what you do in your current job"
        error={errors.currentJobDescription?.message}
        {...register('currentJobDescription')}
      />

      <div>
        <label htmlFor={recencyId} className={labelClass}>
          Only show jobs posted in the last
        </label>
        <select
          id={recencyId}
          className={`${inputClass(!!errors.recencyWindowDays)} sm:w-48`}
          {...register('recencyWindowDays', { valueAsNumber: true })}
        >
          {RECENCY_OPTIONS.map((days) => (
            <option key={days} value={days}>
              {days} days
            </option>
          ))}
        </select>
      </div>

      <div className="sm:w-56">
        <SubmitButton loading={isSubmitting}>{submitLabel}</SubmitButton>
      </div>
    </form>
  )
}
