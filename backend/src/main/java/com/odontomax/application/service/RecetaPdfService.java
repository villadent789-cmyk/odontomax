package com.odontomax.application.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.request.RecetaRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class RecetaPdfService {

    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    public byte[] generarRecetaPdf(RecetaRequestDTO dto) {
        if (dto.getPacienteId() != null) {
            consultaEspecialistasApi.exigirAccesoDeOdontologo(dto.getPacienteId());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Tipografías
            Font clinicFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(180, 83, 9)); // Amber-700
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font tableBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            // 1. Cabecera Institucional
            Paragraph header = new Paragraph("CLÍNICA ODONTOLÓGICA ODONTOMAX", clinicFont);
            header.setAlignment(Element.ALIGN_CENTER);
            doc.add(header);

            Paragraph subHeader = new Paragraph("RECETA ODONTOLÓGICA E INDICACIONES TERAPÉUTICAS", subtitleFont);
            subHeader.setAlignment(Element.ALIGN_CENTER);
            subHeader.setSpacingAfter(15);
            doc.add(subHeader);

            // 2. Datos del Paciente y Consulta
            PdfPTable patientTable = new PdfPTable(2);
            patientTable.setWidthPercentage(100);
            patientTable.setSpacingAfter(15);

            PdfPCell c1 = new PdfPCell();
            c1.setBorder(Rectangle.BOX);
            c1.setPadding(8);
            c1.addElement(new Paragraph("Paciente: " + dto.getPacienteNombre(), sectionTitleFont));
            if (dto.getPacienteDni() != null) {
                c1.addElement(new Paragraph("DNI: " + dto.getPacienteDni(), normalFont));
            }
            if (dto.getPacienteEdad() != null) {
                c1.addElement(new Paragraph("Edad: " + dto.getPacienteEdad() + " años", normalFont));
            }

            PdfPCell c2 = new PdfPCell();
            c2.setBorder(Rectangle.BOX);
            c2.setPadding(8);
            c2.addElement(new Paragraph("Fecha: " + (dto.getFecha() != null ? dto.getFecha().toString() : ""), normalFont));
            c2.addElement(new Paragraph("Sede: " + (dto.getSedeNombre() != null ? dto.getSedeNombre() : "Sede Principal"), normalFont));
            if (dto.getDiagnostico() != null && !dto.getDiagnostico().isBlank()) {
                c2.addElement(new Paragraph("Diagnóstico: " + dto.getDiagnostico(), normalFont));
            }

            patientTable.addCell(c1);
            patientTable.addCell(c2);
            doc.add(patientTable);

            // 3. Tabla de Medicamentos / Posología
            Paragraph rxTitle = new Paragraph("PRESCRIPCIÓN FARMACOLÓGICA (Rx):", sectionTitleFont);
            rxTitle.setSpacingAfter(6);
            doc.add(rxTitle);

            PdfPTable medTable = new PdfPTable(5);
            medTable.setWidthPercentage(100);
            medTable.setWidths(new float[]{3.0f, 1.5f, 1.8f, 1.5f, 2.2f});
            medTable.setSpacingAfter(15);

            Color headerColor = new Color(51, 65, 85); // Slate-700
            String[] headers = {"Medicamento", "Dosis", "Frecuencia", "Duración", "Indicaciones"};
            for (String h : headers) {
                PdfPCell th = new PdfPCell(new Phrase(h, tableHeaderFont));
                th.setBackgroundColor(headerColor);
                th.setPadding(6);
                th.setHorizontalAlignment(Element.ALIGN_CENTER);
                medTable.addCell(th);
            }

            if (dto.getMedicamentos() != null) {
                for (RecetaRequestDTO.ItemRecetaDTO item : dto.getMedicamentos()) {
                    PdfPCell cellMed = new PdfPCell(new Phrase(item.getMedicamento() != null ? item.getMedicamento() : "", tableBodyFont));
                    cellMed.setPadding(5);
                    medTable.addCell(cellMed);

                    PdfPCell cellDosis = new PdfPCell(new Phrase(item.getDosis() != null ? item.getDosis() : "-", tableBodyFont));
                    cellDosis.setPadding(5);
                    medTable.addCell(cellDosis);

                    PdfPCell cellFrec = new PdfPCell(new Phrase(item.getFrecuencia() != null ? item.getFrecuencia() : "-", tableBodyFont));
                    cellFrec.setPadding(5);
                    medTable.addCell(cellFrec);

                    PdfPCell cellDur = new PdfPCell(new Phrase(item.getDuracion() != null ? item.getDuracion() : "-", tableBodyFont));
                    cellDur.setPadding(5);
                    medTable.addCell(cellDur);

                    PdfPCell cellInd = new PdfPCell(new Phrase(item.getIndicaciones() != null ? item.getIndicaciones() : "-", tableBodyFont));
                    cellInd.setPadding(5);
                    medTable.addCell(cellInd);
                }
            }
            doc.add(medTable);

            // 4. Indicaciones Generales y Cuidados Post-Tratamiento
            if (dto.getIndicacionesGenerales() != null && !dto.getIndicacionesGenerales().isBlank()) {
                Paragraph indTitle = new Paragraph("INDICACIONES Y CUIDADOS GENERALES:", sectionTitleFont);
                indTitle.setSpacingAfter(4);
                doc.add(indTitle);

                Paragraph indBody = new Paragraph(dto.getIndicacionesGenerales(), normalFont);
                indBody.setSpacingAfter(25);
                doc.add(indBody);
            } else {
                Paragraph spacer = new Paragraph("\n");
                spacer.setSpacingAfter(20);
                doc.add(spacer);
            }

            // 5. Firma y Sello del Odontólogo
            PdfPTable footerTable = new PdfPTable(2);
            footerTable.setWidthPercentage(100);

            PdfPCell legalCell = new PdfPCell();
            legalCell.setBorder(Rectangle.NO_BORDER);
            legalCell.addElement(new Paragraph("Válido para adquisición en farmacia.", smallFont));
            legalCell.addElement(new Paragraph("En caso de reacción adversa suspenda el fármaco y acuda a la clínica.", smallFont));

            PdfPCell signCell = new PdfPCell();
            signCell.setBorder(Rectangle.NO_BORDER);
            signCell.setHorizontalAlignment(Element.ALIGN_CENTER);

            Paragraph line = new Paragraph("________________________________________", normalFont);
            line.setAlignment(Element.ALIGN_CENTER);
            signCell.addElement(line);

            Paragraph docName = new Paragraph("Dr(a). " + dto.getDoctorNombre(), sectionTitleFont);
            docName.setAlignment(Element.ALIGN_CENTER);
            signCell.addElement(docName);

            if (dto.getDoctorCop() != null) {
                Paragraph cop = new Paragraph(dto.getDoctorCop(), normalFont);
                cop.setAlignment(Element.ALIGN_CENTER);
                signCell.addElement(cop);
            }

            footerTable.addCell(legalCell);
            footerTable.addCell(signCell);
            doc.add(footerTable);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar receta médica en PDF: " + e.getMessage(), e);
        }
    }
}
