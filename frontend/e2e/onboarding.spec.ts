import { expect, signIn, test, USER } from './app'

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

test('a user picks the companies to search from the full list, by sector', async ({ page, backend }) => {
  await signIn(page)
  await page.getByLabel(/Role you/).fill('Senior Java Developer')
  await page.getByRole('combobox', { name: /Companies to search/ }).fill('A Company Not Listed')
  await page.getByRole('combobox', { name: /Companies to search/ }).press('Enter')

  await page.getByRole('button', { name: 'Browse all companies' }).click()
  const browser = page.getByRole('dialog', { name: 'Browse companies' })
  await browser.getByText('By sector').click()
  await expect(browser.getByRole('heading', { level: 3 })).toHaveText([
    /^Big Tech & Software/,
    /^Consulting & IT Services/,
    /^Fintech/,
    /^Retail & E-commerce/,
  ])
  await browser.getByRole('list', { name: 'Fintech' }).getByRole('checkbox', { name: 'Monzo' }).check()
  await browser.getByRole('list', { name: 'Big Tech & Software' }).getByRole('checkbox', { name: 'Dell' }).check()
  await expect(browser.getByText('3 of 10')).toBeVisible()
  await browser.getByRole('button', { name: 'Done' }).click()
  await expect(browser).toBeHidden()

  await page.getByRole('button', { name: 'Save and continue' }).click()
  await expect(page).toHaveURL('/')
  expect(backend.savedProfiles[0]?.companyPreferences).toEqual(['A Company Not Listed', 'Monzo', 'Dell'])
})

test('a user without an invite waits on the waitlist, and sets up their search once invited', async ({
  page,
  backend,
}) => {
  backend.access = 'PENDING'
  await signIn(page)

  await expect(page.getByRole('heading', { name: 'You’re on the waitlist' })).toBeVisible()
  await expect(page.getByText(USER.email)).toBeVisible()
  // The app itself isn't reachable while waiting.
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: 'You’re on the waitlist' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeHidden()

  backend.access = 'APPROVED'
  await page.getByRole('button', { name: 'Check again' }).click()

  await expect(page).toHaveURL('/onboarding')
})
