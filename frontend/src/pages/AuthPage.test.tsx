import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AuthPage } from './AuthPage'

describe('AuthPage', () => {
  it('switches from login to secure local registration', async () => {
    const user = userEvent.setup()
    render(<AuthPage />)

    expect(screen.getByRole('heading', { name: 'Open your ledger' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create a local account' }))

    expect(screen.getByRole('heading', { name: 'Create your workspace' })).toBeInTheDocument()
    expect(screen.getByLabelText('Display name')).toBeRequired()
    expect(screen.getByLabelText('Password')).toHaveAttribute('minLength', '12')
  })
})

