import { expect, signIn, test } from './app'

test('a user without a profile sets one up and reaches an empty dashboard', async ({ page, backend }) => {
  await signIn(page)

  await expect(page).toHaveURL('/onboarding')
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeHidden()

  // The dashboard isn't reachable until the profile exists.
  await page.goto('/')
  await expect(page).toHaveURL('/onboarding')

  await page.getByLabel(/Role you/).fill('Senior Java Developer')
  const companies = page.getByRole('combobox', { name: /Companies to search/ })
  await companies.fill('del')
  const suggestions = page.getByRole('listbox', { name: 'Companies to search suggestions' })
  await expect(suggestions.getByRole('option')).toHaveText(['Deliveroo', 'Dell', 'Deloitte'])
  await suggestions.getByRole('option', { name: 'Dell' }).click()
  await companies.fill('A Company Not Listed')
  await expect(suggestions).toBeHidden() // nothing known starts with this
  await companies.press('Enter')
  const skills = page.getByRole('combobox', { name: /Key skills/ })
  await skills.fill('jav')
  await expect(page.getByRole('listbox', { name: 'Key skills suggestions' }).getByRole('option')).toHaveText([
    'Java',
    'JavaScript',
  ])
  await skills.press('Enter') // completes to the first suggestion
  await page.getByLabel('Only show jobs posted in the last').selectOption('30 days')
  await page.getByRole('button', { name: 'Save and continue' }).click()

  await expect(page).toHaveURL('/')
  await expect(page.getByText('No job matches yet')).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible()
  expect(backend.savedProfiles).toEqual([
    {
      desiredRole: 'Senior Java Developer',
      skills: ['Java'],
      currentJobDescription: null,
      companyPreferences: ['Dell', 'A Company Not Listed'],
      recencyWindowDays: 30,
    },
  ])
})

test('the profile is not saved until a role and a company are given', async ({ page, backend }) => {
  await signIn(page)
  await page.getByRole('button', { name: 'Save and continue' }).click()

  await expect(page.getByText('Enter the role you’re looking for')).toBeVisible()
  await expect(page.getByText('Add at least one company to search')).toBeVisible()
  await expect(page).toHaveURL('/onboarding')
  expect(backend.savedProfiles).toEqual([])
})
