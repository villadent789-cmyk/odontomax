import type { TipoEstudioRadiografia } from './api-radiografias'

/** Tipos de estudio que se pueden subir a la historia del paciente (y su etiqueta en pantalla). */
export const TIPOS_ESTUDIO: { valor: TipoEstudioRadiografia; etiqueta: string; villamax?: boolean }[] = [
  { valor: 'PANORAMICA', etiqueta: 'Panorámica Digital HD (Ortopantomografía)' },
  { valor: 'TOMOGRAFIA', etiqueta: 'Tomografía Cone Beam 3D (Villamax CBCT)', villamax: true },
  { valor: 'PERIAPICAL', etiqueta: 'Radiografía Periapical' },
  { valor: 'ALETA_MORDIDA', etiqueta: 'Aleta de Mordida (Bitewing)' },
  { valor: 'CEFALOMETRICA', etiqueta: 'Cefalométrica / Telerradiografía Lateral' },
  { valor: 'FOTO_CLINICA', etiqueta: 'Fotografía Clínica (Intra/Extraoral)' },
  { valor: 'FOTO_ANTES', etiqueta: 'Foto Antes del Tratamiento' },
  { valor: 'FOTO_DESPUES', etiqueta: 'Foto Resultado Final' },
  { valor: 'INFORME', etiqueta: 'Informe Radiológico Especializado' },
]
