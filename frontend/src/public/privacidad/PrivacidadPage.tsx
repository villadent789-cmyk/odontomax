import { Link } from '@tanstack/react-router'

/**
 * Política de privacidad de la web pública (Ley N.º 29733, Protección de Datos Personales). Es un texto base: antes de salir a
 * producción debe revisarlo quien asesora legalmente a la clínica y completarse la razón social, el RUC, el domicilio y el
 * correo de contacto (ver docs/despliegue.md, «Antes de abrir al público»).
 */
export function PrivacidadPage() {
  return (
    <article className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <header className="flex flex-col gap-2">
        <h1 className="font-display text-3xl font-bold text-on-surface">Política de privacidad</h1>
        <p className="text-sm text-on-surface-variant">
          Cómo cuidamos tus datos personales y de salud en Villadent (odontología) y Villamax (radiología).
        </p>
      </header>

      <section aria-labelledby="priv-quien" className="flex flex-col gap-2">
        <h2 id="priv-quien" className="font-display text-xl font-bold text-on-surface">Quién es responsable de tus datos</h2>
        <p className="text-sm leading-relaxed text-on-surface">
          Villadent · Villamax es el titular del banco de datos donde se guardan tus datos. Cumplimos la Ley N.º 29733, Ley de
          Protección de Datos Personales, y su reglamento.
        </p>
      </section>

      <section aria-labelledby="priv-datos" className="flex flex-col gap-2">
        <h2 id="priv-datos" className="font-display text-xl font-bold text-on-surface">Qué datos recogemos</h2>
        <ul className="list-disc space-y-1 pl-5 text-sm leading-relaxed text-on-surface">
          <li>Identificación y contacto: DNI o documento, nombres, apellidos, celular o WhatsApp y fecha de nacimiento.</li>
          <li>
            Datos de salud (datos sensibles): historia clínica, antecedentes médicos, alergias, odontograma, tratamientos,
            radiografías, tomografías y fotografías clínicas.
          </li>
          <li>Datos de atención y pago: citas, planes de tratamiento, pagos y comprobantes.</li>
        </ul>
      </section>

      <section aria-labelledby="priv-uso" className="flex flex-col gap-2">
        <h2 id="priv-uso" className="font-display text-xl font-bold text-on-surface">Para qué los usamos</h2>
        <ul className="list-disc space-y-1 pl-5 text-sm leading-relaxed text-on-surface">
          <li>Gestionar tus citas y recordártelas por llamada o WhatsApp.</li>
          <li>Brindarte la atención odontológica o radiológica y llevar tu historia clínica.</li>
          <li>Cobrar tus tratamientos y emitir tus comprobantes.</li>
          <li>Cumplir las obligaciones que la normativa de salud y tributaria nos exige.</li>
        </ul>
        <p className="text-sm leading-relaxed text-on-surface">
          No vendemos tus datos ni los usamos para publicidad de terceros. Tus datos de salud solo los ve el personal que
          participa en tu atención, y cada acceso a tu historia queda registrado.
        </p>
      </section>

      <section aria-labelledby="priv-compartir" className="flex flex-col gap-2">
        <h2 id="priv-compartir" className="font-display text-xl font-bold text-on-surface">Con quién los compartimos</h2>
        <p className="text-sm leading-relaxed text-on-surface">
          Solo con proveedores que guardan o procesan la información por encargo nuestro (por ejemplo, el servicio de base de
          datos y de almacenamiento de archivos), bajo medidas de seguridad y confidencialidad. También con las autoridades, cuando
          la ley lo exija.
        </p>
      </section>

      <section aria-labelledby="priv-seguridad" className="flex flex-col gap-2">
        <h2 id="priv-seguridad" className="font-display text-xl font-bold text-on-surface">Cómo los protegemos</h2>
        <p className="text-sm leading-relaxed text-on-surface">
          El sistema del personal exige usuario y contraseña, con verificación en dos pasos, y cada persona solo ve lo que su
          cargo necesita. Las copias de seguridad se guardan cifradas. Esta web no usa cookies de publicidad ni herramientas de
          rastreo: solo guarda en tu navegador lo imprescindible para que funcione, como el código de tu reserva.
        </p>
      </section>

      <section aria-labelledby="priv-derechos" className="flex flex-col gap-2">
        <h2 id="priv-derechos" className="font-display text-xl font-bold text-on-surface">Tus derechos</h2>
        <p className="text-sm leading-relaxed text-on-surface">
          Puedes pedirnos conocer qué datos tuyos tenemos (acceso), corregirlos (rectificación), cancelarlos u oponerte a su uso,
          en los casos que la ley permite. Escríbenos por WhatsApp al 960 112 257 o acércate a recepción con tu DNI. Si crees que
          no atendimos tu pedido, puedes acudir a la Autoridad Nacional de Protección de Datos Personales.
        </p>
      </section>

      <section aria-labelledby="priv-consentimiento" className="flex flex-col gap-2">
        <h2 id="priv-consentimiento" className="font-display text-xl font-bold text-on-surface">Tu consentimiento</h2>
        <p className="text-sm leading-relaxed text-on-surface">
          Al reservar una cita nos autorizas a usar los datos que ingresas para gestionarla. Para atenderte te pediremos,
          además, que firmes el consentimiento informado en la clínica.
        </p>
      </section>

      <p className="text-sm">
        <Link to="/" className="font-semibold text-primary hover:underline">Volver al inicio</Link>
      </p>
    </article>
  )
}
