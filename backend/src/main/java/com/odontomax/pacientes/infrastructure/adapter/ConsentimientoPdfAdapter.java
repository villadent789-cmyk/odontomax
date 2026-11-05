package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.pacientes.application.port.out.ConsentimientoPdfPort;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;

@Component
public class ConsentimientoPdfAdapter implements ConsentimientoPdfPort {

    @Override
    public byte[] generarDocumentoPdf(
            String titulo,
            String pacienteNombre,
            String tipo,
            String contenido,
            String firmante,
            String documento,
            OffsetDateTime fecha
    ) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4, 40, 40, 50, 40);
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, baos);
            doc.open();

            com.lowagie.text.Font titleFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 15);
            com.lowagie.text.Font boldFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 10);
            com.lowagie.text.Font normalFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 10);
            com.lowagie.text.Font draftFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 9, new java.awt.Color(180, 50, 50));

            com.lowagie.text.Paragraph draftPara = new com.lowagie.text.Paragraph("[BORRADOR - Requiere validación por asesor legal y colegiatura profesional]", draftFont);
            draftPara.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            draftPara.setSpacingAfter(8);
            doc.add(draftPara);

            com.lowagie.text.Paragraph titlePara = new com.lowagie.text.Paragraph(titulo != null ? titulo : "Consentimiento Informado", titleFont);
            titlePara.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(15);
            doc.add(titlePara);

            com.lowagie.text.Paragraph infoPara = new com.lowagie.text.Paragraph();
            infoPara.setFont(normalFont);
            infoPara.add("Paciente: " + (pacienteNombre != null ? pacienteNombre : "") + "\n");
            infoPara.add("Documento de Identidad: " + (documento != null ? documento : "") + "\n");
            infoPara.add("Tipo: " + (tipo != null ? tipo : "") + "\n");
            infoPara.add("Fecha: " + (fecha != null ? fecha.toLocalDate().toString() : "") + "\n\n");
            doc.add(infoPara);

            com.lowagie.text.Paragraph bodyPara = new com.lowagie.text.Paragraph(contenido != null ? contenido : "", normalFont);
            bodyPara.setSpacingAfter(20);
            doc.add(bodyPara);

            com.lowagie.text.Paragraph signPara = new com.lowagie.text.Paragraph();
            signPara.setFont(boldFont);
            signPara.add("Firmado digitalmente en tablet por: " + (firmante != null ? firmante : "") + "\n");
            signPara.add("N° Documento Firmante: " + (documento != null ? documento : "") + "\n");
            doc.add(signPara);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar documento PDF de consentimiento con OpenPDF: " + e.getMessage(), e);
        }
    }
}