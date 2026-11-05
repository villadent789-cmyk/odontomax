const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

const dos = (n: number) => String(n).padStart(2, '0')

/** Mes anterior al actual en formato AAAA-MM: el que normalmente se cierra. */
export function mesAnteriorISO(hoy = new Date()): string {
  const d = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1)
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}`
}

export const mesActualISO = () => {
  const h = new Date()
  return `${h.getFullYear()}-${dos(h.getMonth() + 1)}`
}

/** Primer y último día del mes AAAA-MM, más su nombre legible («octubre de 2026»). */
export function rangoDelMes(mes: string) {
  const [anio, m] = mes.split('-').map(Number)
  const ultimo = new Date(anio, m, 0).getDate()
  return { desde: `${mes}-01`, hasta: `${mes}-${dos(ultimo)}`, nombre: `${MESES[m - 1]} de ${anio}` }
}
