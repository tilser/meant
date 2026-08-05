import { createFileRoute } from '@tanstack/react-router'

import { LoginPage } from '../features/meant/auth/LoginPage'

export const Route = createFileRoute('/login')({ component: LoginPage })
