import { useState } from 'react'
import { Tabs, TabsList, TabsTrigger } from '@/design-system/components/Tabs'
import { SedesPanel } from './SedesPanel'
import { ServiciosPanel } from './ServiciosPanel'
import { UsuariosPanel } from './UsuariosPanel'

const SECCIONES = [
  { id: 'usuarios', etiqueta: 'Usuarios' },
  { id: 'sedes', etiqueta: 'Sedes' },
  { id: 'servicios', etiqueta: 'Servicios' },
] as const

type Seccion = (typeof SECCIONES)[number]['id']

/** Módulo 8 · Administración: usuarios (doctores, recepción, administradores), sedes y catálogo de servicios. Solo ADMIN_GENERAL. */
export function AdministracionPage() {
  const [seccion, setSeccion] = useState<Seccion>('usuarios')
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <header>
        <h1 className="font-display text-headline-lg font-bold">Administración</h1>
        <p className="text-on-surface-variant">Quién usa el sistema, en qué sedes trabaja la clínica y qué servicios ofrece.</p>
      </header>

      <Tabs value={seccion} onValueChange={(v: string) => setSeccion(v as Seccion)}>
        <TabsList className="justify-start">
          {SECCIONES.map((s) => (
            <TabsTrigger key={s.id} value={s.id}>
              {s.etiqueta}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {seccion === 'usuarios' ? <UsuariosPanel /> : seccion === 'sedes' ? <SedesPanel /> : <ServiciosPanel />}
    </div>
  )
}
