export type EstadoCarga = 'cargando' | 'error' | 'ok'

export const estadoDe = (cargando: boolean, error: boolean): EstadoCarga => (cargando ? 'cargando' : error ? 'error' : 'ok')
