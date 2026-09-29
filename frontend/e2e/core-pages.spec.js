import { test, expect } from '@playwright/test'

test('login page exposes account, password and captcha controls', async ({ page }) => {
  await page.route('**/api/auth/captcha', route => route.fulfill({
    json: { captchaId: 'captcha-id', imageBase64: 'data:image/svg+xml;base64,PHN2Zy8+' }
  }))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '平台账号登录' })).toBeVisible()
  await expect(page.getByPlaceholder(/用户名/)).toBeVisible()
  await expect(page.getByPlaceholder(/密码/)).toBeVisible()
  await expect(page.getByPlaceholder('图形验证码')).toBeVisible()
  await expect(page.getByRole('img', { name: /验证码/ })).toBeVisible()
})

test('registration and password recovery expose phone verification', async ({ page }) => {
  await page.route('**/api/auth/captcha', route => route.fulfill({ json: { captchaId: 'captcha-id', imageBase64: 'data:image/svg+xml;base64,PHN2Zy8+' } }))
  await page.route('**/api/sms/send', async route => {
    const payload = route.request().postDataJSON()
    expect(payload.phone).toBe('13800138000')
    expect(['register', 'recovery']).toContain(payload.purpose)
    await route.fulfill({ json: { requestId: 'sms-id', retryAfter: 60 } })
  })
  await page.goto('/login')

  await page.getByText('注册账号', { exact: true }).click()
  await expect(page.getByPlaceholder('手机号')).toBeVisible()
  await page.getByPlaceholder('手机号').fill('13800138000')
  await page.getByRole('button', { name: '发送验证码' }).click()
  await expect(page.getByRole('status')).toContainText('短信验证码已发送')
  await expect(page.getByRole('button', { name: /秒后重发/ })).toBeDisabled()

  await page.getByText('找回密码', { exact: true }).click()
  await expect(page.getByText('使用用户名和账号绑定的手机号验证身份。')).toBeVisible()
  await expect(page.getByPlaceholder('手机号')).toBeVisible()
  await expect(page.getByPlaceholder('6位短信验证码')).toBeVisible()
  await expect(page.getByRole('button', { name: '发送验证码' })).toBeVisible()
})

test('public core routes render without server errors', async ({ page }) => {
  for (const route of ['/topics', '/solutions', '/articles', '/supplier/apply']) {
    await page.goto(route)
    await expect(page.locator('body')).not.toContainText('Cannot GET')
  }
})
