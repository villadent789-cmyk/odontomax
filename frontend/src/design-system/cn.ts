import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

// tailwind-merge debe conocer las escalas propias del preset para no descartar clases como `text-body-lg`.
const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      'font-size': [{ text: ['headline-xl', 'headline-lg', 'headline-md', 'body-lg', 'body-md'] }],
      rounded: [{ rounded: ['card', 'panel', 'control', 'pill'] }],
      shadow: [{ shadow: ['card', 'raised', 'accion'] }],
      tracking: [{ tracking: ['etiqueta'] }],
    },
  },
})

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
