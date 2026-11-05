import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'

async function renderEn(ruta: string) {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
}

describe('Política de privacidad (Ley 29733)', () => {
  it('se abre sin iniciar sesión, explica datos, finalidad y derechos, y está enlazada en el pie de la web', async () => {
    await renderEn('/privacidad')

    expect(screen.getByRole('heading', { level: 1, name: 'Política de privacidad' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Qué datos recogemos' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Tus derechos' })).toBeInTheDocument()
    expect(screen.getByText(/Ley N\.º 29733/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Política de privacidad' })).toHaveAttribute('href', '/privacidad')
  })
})
