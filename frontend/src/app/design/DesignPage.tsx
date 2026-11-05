import { CalendarPlus, Inbox, ScanLine, Stethoscope, UserPlus, Wallet } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  EmptyState,
  ErrorState,
  ESTADOS,
  EstadoBadge,
  Eyebrow,
  Field,
  Input,
  Kbd,
  Skeleton,
  StatCard,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  toast,
} from '@/design-system'
import { MINIMO_AA, PARES_CONTRASTE, leerTokensColor, ratioContraste } from '@/design-system/contrast'
import tokensCss from '@/design-system/tokens.css?raw'
import { formatFecha, formatHora, formatSoles } from '@/shared/format'
import { DemoFormulario } from './DemoFormulario'

const tokens = leerTokensColor(tokensCss)

function Seccion({ id, titulo, children, nota }: { id: string; titulo: string; nota?: ReactNode; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-5 rounded-card bg-surface-container-lowest p-6 shadow-card md:p-8">
      <div className="flex flex-col gap-1">
        <h2 id={id} className="font-display text-headline-md font-bold">
          {titulo}
        </h2>
        {nota && <p className="text-sm text-on-surface-variant">{nota}</p>}
      </div>
      {children}
    </section>
  )
}

function Muestra({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-bold text-on-surface-variant">{etiqueta}</span>
      {children}
    </div>
  )
}

const COLORES = [
  ['primary', 'Villadent Slate (Logo)'],
  ['primary-container', 'Botón Primario Slate'],
  ['secondary-container', 'Acción Clínica (Zafiro)'],
  ['tertiary', 'Índigo Villamax'],
  ['error', 'Error'],
  ['error-container', 'Fondo de error/bloqueo'],
  ['primary-fixed', 'Chip primario'],
  ['primary-fixed-dim', 'Acento tenue'],
  ['surface', 'Fondo clínico neutro'],
  ['surface-container-low', 'Barra lateral / campos'],
  ['surface-container-high', 'Botón tonal / hover'],
  ['on-surface', 'Texto principal'],
  ['on-surface-variant', 'Texto secundario'],
  ['outline-variant', 'Separadores'],
] as const

export function DesignPage() {
  const [alertaVisible, setAlertaVisible] = useState(true)
  return (
    <div className="min-h-dvh bg-surface">
      <header className="border-b border-outline-variant bg-surface-container-low px-6 py-8 md:px-12">
        <Badge tono="accion" mayusculas>
          Solo en desarrollo
        </Badge>
        <h1 className="mt-3 font-display text-headline-xl font-bold">Guía de estilo del sistema</h1>
        <p className="mt-2 max-w-3xl text-body-lg text-on-surface-variant">
          Componentes del design system de Villadent · Villamax con sus variantes y estados. Recorre la página con{' '}
          <Kbd>Tab</Kbd> para revisar el foco visible.
        </p>
      </header>

      <main className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-8 md:px-8">
        <Seccion id="colores" titulo="1. Colores y contraste" nota="Ratios WCAG calculados sobre tokens.css (mínimo AA: 4.5:1 texto normal, 3:1 texto grande y bordes/foco).">
          <ul className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-7">
            {COLORES.map(([token, nombre]) => (
              <li key={token} className="flex flex-col gap-1.5">
                <span
                  aria-hidden
                  className="h-16 rounded-panel border border-outline-variant"
                  style={{ backgroundColor: `var(--vd-${token})` }}
                />
                <span className="text-sm font-bold">{nombre}</span>
                <code className="font-mono text-xs text-on-surface-variant">
                  {token} · {tokens[token]}
                </code>
              </li>
            ))}
          </ul>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[40rem] border-collapse text-left text-sm">
              <caption className="sr-only">Pares de contraste texto/fondo</caption>
              <thead>
                <tr className="border-b border-outline-variant">
                  <th scope="col" className="py-2 pr-4">Muestra</th>
                  <th scope="col" className="py-2 pr-4">Texto / fondo</th>
                  <th scope="col" className="py-2 pr-4">Uso</th>
                  <th scope="col" className="py-2 pr-4">Ratio</th>
                  <th scope="col" className="py-2">AA</th>
                </tr>
              </thead>
              <tbody>
                {PARES_CONTRASTE.map((par) => {
                  const ratio = ratioContraste(tokens[par.texto], tokens[par.fondo])
                  const ok = ratio >= MINIMO_AA[par.tamano]
                  return (
                    <tr key={`${par.texto}-${par.fondo}`} className="border-b border-outline-variant/60">
                      <td className="py-2 pr-4">
                        <span
                          className="inline-block rounded-md px-2 py-1 font-bold"
                          style={{ color: `var(--vd-${par.texto})`, backgroundColor: `var(--vd-${par.fondo})` }}
                        >
                          Aa
                        </span>
                      </td>
                      <td className="py-2 pr-4 font-mono text-xs">
                        {par.texto} / {par.fondo}
                      </td>
                      <td className="py-2 pr-4">{par.uso}</td>
                      <td className="py-2 pr-4 font-bold">{ratio.toFixed(2)}:1</td>
                      <td className="py-2">
                        <Badge tono={ok ? 'primario' : 'peligro'}>
                          {ok ? 'Cumple' : 'No cumple'} ({par.tamano === 'normal' ? '4.5' : '3'})
                        </Badge>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </Seccion>

        <Seccion id="tipografia" titulo="2. Tipografía" nota="Bricolage Grotesque para títulos y énfasis; Plus Jakarta Sans para lectura y formularios (guía A1).">
          <div className="flex flex-col gap-4">
            <Eyebrow>Bricolage Grotesque · títulos (headline-xl 700, headline-lg y headline-md 600)</Eyebrow>
            <p className="font-display text-headline-xl font-bold text-primary">Salud bucal con confianza humana</p>
            <p className="font-display text-headline-lg font-semibold text-on-surface">Especialistas en sonrisas saludables</p>
            <p className="font-display text-headline-md font-semibold text-on-surface">Innovación diagnóstica en Huancayo</p>
            <Eyebrow>Plus Jakarta Sans · lectura y formularios</Eyebrow>
            <p className="text-body-lg">Tratamientos diseñados para toda la familia, desde profilaxis básica hasta ortodoncia avanzada.</p>
            <p className="text-body-md text-on-surface-variant">Contamos con protocolos de bioseguridad certificados.</p>
            <Eyebrow>Etiqueta en mayúsculas con tracking</Eyebrow>
            <p className="text-sm text-on-surface-variant">
              Formatos: {formatSoles(1234.5)} · {formatFecha('2024-10-24')} · {formatHora('2024-10-24T15:45:00Z')} (hora de Lima)
            </p>
          </div>
        </Seccion>

        <Seccion id="botones" titulo="3. Botones" nota="Píldora. El coral se reserva para la acción principal de cada pantalla.">
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            <Muestra etiqueta="Acción (coral)"><Button variant="accion"><UserPlus aria-hidden />Registrar paciente</Button></Muestra>
            <Muestra etiqueta="Primario (verde)"><Button variant="primario"><CalendarPlus aria-hidden />Agendar cita</Button></Muestra>
            <Muestra etiqueta="Secundario (tonal)"><Button variant="secundario">Ver horarios</Button></Muestra>
            <Muestra etiqueta="Villamax (índigo)"><Button variant="villamax"><ScanLine aria-hidden />Tomografía 3D</Button></Muestra>
            <Muestra etiqueta="Peligro"><Button variant="peligro">Cancelar cita</Button></Muestra>
            <Muestra etiqueta="Fantasma"><Button variant="fantasma">Más opciones</Button></Muestra>
            <Muestra etiqueta="Enlace"><Button variant="enlace">¿Olvidaste tu contraseña?</Button></Muestra>
            <Muestra etiqueta="Deshabilitado"><Button variant="accion" disabled>No disponible</Button></Muestra>
            <Muestra etiqueta="Cargando"><Button variant="primario" cargando>Guardando…</Button></Muestra>
            <Muestra etiqueta="Foco (simulado)"><Button variant="secundario" className="outline-2 outline-offset-2 outline-focus">Con foco</Button></Muestra>
            <Muestra etiqueta="Tamaños">
              <div className="flex flex-wrap items-center gap-2">
                <Button size="sm">Pequeño</Button>
                <Button size="lg">Grande</Button>
              </div>
            </Muestra>
            <Muestra etiqueta="Solo ícono"><Button size="icono" variant="secundario" aria-label="Cobrar"><Wallet aria-hidden /></Button></Muestra>
          </div>
        </Seccion>

        <Seccion id="formularios" titulo="4. Campos de formulario" nota="Normal, error (DNI precargado inválido: pulsa Validar), deshabilitado y contraseña con mostrar/ocultar.">
          <DemoFormulario />
          <div className="grid gap-5 md:grid-cols-2">
            <Field label="Foco (simulado)">
              <Input defaultValue="964 123 456" className="border-focus bg-surface-container-lowest outline-2 outline-focus" />
            </Field>
            <Field label="DNI (estado error)" error="DNI inválido: debe contener 8 dígitos numéricos.">
              <Input defaultValue="4590" />
            </Field>
          </div>
        </Seccion>

        <Seccion id="etiquetas" titulo="5. Etiquetas y estados" nota="EstadoBadge usa un mapa único estado→texto/color (ESTADOS). Abajo, todos los estados del mapa real.">
          <div className="flex flex-wrap gap-3">
            <Badge>Neutro</Badge>
            <Badge tono="primario">Primario</Badge>
            <Badge tono="accion">Acción</Badge>
            <Badge tono="villamax">Villamax</Badge>
            <Badge tono="peligro">Peligro</Badge>
            <Badge tono="claro" className="shadow-card">Claro</Badge>
            <Badge tono="primario" mayusculas>Servicio activo</Badge>
          </div>
          <div className="flex flex-wrap gap-3">
            {Object.keys(ESTADOS).map((e) => (
              <EstadoBadge key={e} estado={e} />
            ))}
            <EstadoBadge estado="PENDIENTE_DE_PAGO" />
          </div>
          <p className="text-sm text-on-surface-variant">
            El último chip muestra un estado no mapeado: se humaniza el texto y se usa el tono neutro.
          </p>
        </Seccion>

        <Seccion id="avisos" titulo="6. Avisos">
          {alertaVisible ? (
            <Alert tono="error" titulo="Error de autenticación" onCerrar={() => setAlertaVisible(false)}>
              Correo corporativo o contraseña incorrectos.
            </Alert>
          ) : (
            <Button variant="secundario" onClick={() => setAlertaVisible(true)}>Mostrar de nuevo el aviso</Button>
          )}
          <Alert tono="bloqueo" titulo="Bloqueo preventivo del sistema">
            Por seguridad, tu cuenta se bloqueó temporalmente tras varios intentos fallidos. Intenta de nuevo en <strong>15 minutos</strong>.
          </Alert>
          <Alert tono="info" titulo="Información">El cierre de caja se realiza al final de cada turno.</Alert>
          <Alert tono="exito" titulo="Cambios guardados">Los datos del paciente se actualizaron.</Alert>
          <div className="flex flex-wrap gap-3">
            <Button variant="secundario" onClick={() => toast('Cita reprogramada', { description: 'Se notificó al paciente.' })}>Toast informativo</Button>
            <Button variant="secundario" onClick={() => toast.success('Pago registrado')}>Toast de éxito</Button>
            <Button variant="secundario" onClick={() => toast.error('No se pudo emitir el comprobante')}>Toast de error</Button>
          </div>
        </Seccion>

        <Seccion id="pestanas" titulo="7. Pestañas" nota="Navegables con las flechas del teclado.">
          <Tabs defaultValue="login">
            <TabsList aria-label="Pasos de acceso">
              <TabsTrigger value="login">1. Login</TabsTrigger>
              <TabsTrigger value="token">2. Token 2FA</TabsTrigger>
              <TabsTrigger value="ajustes">3. Ajustes 2FA</TabsTrigger>
              <TabsTrigger value="recuperar" disabled>4. Recuperar</TabsTrigger>
            </TabsList>
            <TabsContent value="login">Contenido del paso de inicio de sesión.</TabsContent>
            <TabsContent value="token">Contenido del paso de token.</TabsContent>
            <TabsContent value="ajustes">Contenido de ajustes 2FA.</TabsContent>
          </Tabs>
        </Seccion>

        <Seccion id="tarjetas" titulo="8. Tarjetas e indicadores" nota="StatCard solo se usará con datos reales de la API; aquí los valores están marcados como ejemplo.">
          <div className="grid gap-6 md:grid-cols-3">
            <Card>
              <CardHeader>
                <CardTitle>Tarjeta estándar</CardTitle>
                <Badge tono="primario">Activo</Badge>
              </CardHeader>
              <CardDescription>Radio grande y sombra difusa.</CardDescription>
              <CardContent>Contenido de la tarjeta.</CardContent>
              <CardFooter><Button size="sm" variant="secundario">Ver detalle</Button></CardFooter>
            </Card>
            <StatCard etiqueta="Indicador (ejemplo)" valor={formatSoles(1234.5)} complemento="Dato de ejemplo" icono={<Stethoscope />} insignia={<Badge tono="claro">Ejemplo</Badge>} pie="Pie de la tarjeta" />
            <StatCard etiqueta="Indicador cargando" cargando icono={<Wallet />} tonoIcono="accion" />
          </div>
          <div aria-busy="true" className="flex flex-col gap-3">
            <Eyebrow>Esqueleto de carga (lista)</Eyebrow>
            <span className="sr-only">Cargando…</span>
            {[0, 1, 2].map((i) => (
              <div key={i} className="flex items-center gap-4">
                <Skeleton className="size-11 rounded-full" />
                <div className="flex flex-1 flex-col gap-2">
                  <Skeleton className="h-4 w-1/3" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
            ))}
          </div>
        </Seccion>

        <Seccion id="estados" titulo="9. Vacío y error">
          <div className="grid gap-6 md:grid-cols-2">
            <div className="rounded-panel bg-surface-container-low">
              <EmptyState icono={<Inbox />} titulo="No hay citas para hoy" descripcion="Cuando se agenden citas aparecerán aquí." accion={<Button variant="primario"><CalendarPlus aria-hidden />Agendar cita</Button>} />
            </div>
            <div className="rounded-panel bg-surface-container-low">
              <ErrorState onReintentar={() => toast('Reintentando…')} />
            </div>
          </div>
        </Seccion>

        <Seccion id="otros" titulo="10. Diálogo, avatar y teclas">
          <div className="flex flex-wrap items-center gap-6">
            <Dialog>
              <DialogTrigger asChild>
                <Button variant="peligro">Cancelar cita…</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>¿Cancelar la cita?</DialogTitle>
                  <DialogDescription>El paciente recibirá un aviso. Esta acción no se puede deshacer.</DialogDescription>
                </DialogHeader>
                <DialogFooter>
                  <DialogClose asChild><Button variant="secundario">Volver</Button></DialogClose>
                  <DialogClose asChild><Button variant="peligro">Sí, cancelar</Button></DialogClose>
                </DialogFooter>
              </DialogContent>
            </Dialog>
            <div className="flex items-center gap-3">
              <Avatar nombre="Katherin Mendoza" tamano="sm" />
              <Avatar nombre="Katherin Mendoza" />
              <Avatar nombre="Lucía" tamano="lg" />
            </div>
            <p className="flex items-center gap-1 text-on-surface-variant">
              Atajo: <Kbd>Ctrl</Kbd>+<Kbd>K</Kbd>
            </p>
          </div>
        </Seccion>
      </main>
    </div>
  )
}
