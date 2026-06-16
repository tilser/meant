import { createFileRoute } from '@tanstack/react-router'

import { MeantApp } from '../features/meant/MeantApp'

export const Route = createFileRoute('/')({
  component: MeantApp,
})
