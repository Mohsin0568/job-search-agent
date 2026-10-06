import { expect, PROFILE, signIn, test, USER } from './app'

test.beforeEach(({ backend }) => {
  backend.profile = PROFILE
})

test('a signed-out visitor is sent to sign in, then back to the page they asked for', async ({ page }) => {
  await page.goto('/profile')
  await expect(page).toHaveURL('/login')

  await signIn(page, { from: '/profile' })

  await expect(page).toHaveURL('/profile')
  await expect(page.getByRole('heading', { name: 'Search preferences' })).toBeVisible()
  await expect(page.getByText(USER.email)).toBeVisible()
})

test('a refused sign-in is reported and the user stays signed out', async ({ page, backend }) => {
  backend.rejectPassword = true
  await signIn(page)

  await expect(page.getByRole('alert')).toHaveText('Incorrect email or password.')
  await expect(page).toHaveURL('/login')

  await page.goto('/')
  await expect(page).toHaveURL('/login')
})

test('the session survives a reload', async ({ page }) => {
  await signIn(page)
  await expect(page.getByRole('heading', { name: `Welcome, ${USER.name}` })).toBeVisible()

  await page.reload()

  await expect(page.getByRole('heading', { name: `Welcome, ${USER.name}` })).toBeVisible()
  await expect(page).toHaveURL('/')
})

test('signing out returns to sign in and locks the app again', async ({ page }) => {
  await signIn(page)
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL('/login')
  await expect(page.getByText('Your session has expired')).toBeHidden()

  await page.goto('/')

  await expect(page).toHaveURL('/login')
})

test('when the API rejects the token, the user is told the session expired', async ({ page, backend }) => {
  await signIn(page)
  await expect(page.getByRole('heading', { name: `Welcome, ${USER.name}` })).toBeVisible()

  backend.rejectTokens = true
  await page.reload()

  await expect(page).toHaveURL('/login')
  await expect(page.getByRole('status')).toHaveText('Your session has expired. Please sign in again.')
})

test('a new user registers, verifies their email and lands in onboarding', async ({ page, backend }) => {
  backend.profile = null
  await page.goto('/register')
  await page.getByLabel('Full name').fill(USER.name)
  await page.getByLabel('Email').fill(USER.email)
  await page.getByLabel('Password', { exact: true }).fill(USER.password)
  await page.getByLabel('Confirm password').fill(USER.password)
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page).toHaveURL('/confirm')
  await expect(page.getByText(`Code sent to ${USER.email}`)).toBeVisible()
  await page.getByLabel('Verification code').fill('123456')
  await page.getByRole('button', { name: 'Verify email' }).click()

  await expect(page).toHaveURL('/onboarding')
  await expect(page.getByRole('heading', { name: 'Welcome, Ada! Let’s set up your search' })).toBeVisible()
})
