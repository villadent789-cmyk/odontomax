import { createRootRoute } from '@tanstack/react-router'
import { NoEncontrada, RootLayout } from './RootLayout'

export const rootRoute = createRootRoute({
  component: RootLayout,
  notFoundComponent: NoEncontrada,
})
