import { createFileRoute } from '@tanstack/react-router'

import { AuthCallbackPage } from '../features/meant/auth/LoginPage'

export const Route = createFileRoute('/auth/callback')({ component: AuthCallbackPage })
