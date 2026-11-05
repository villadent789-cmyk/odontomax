package com.odontomax.clinica.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.infrastructure.persistence.repository.SpringDataOdontogramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OdontogramaPdfService {

    private final SpringDataOdontogramaRepository odontogramaRepository;
    private final ConsultaPacientesModuloApi pacientesApi;
    private final ConsultaEspecialistasModuloApi consultaEspecialistasApi;

    @Transactional(readOnly = true)
    public byte[] generarPdf(UUID pacienteId) {
        PacienteResumen paciente = pacientesApi.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado: " + pacienteId));

        SecurityUtils.validarAccesoSede(paciente.sedeId());
        consultaEspecialistasApi.exigirAccesoDeOdontologo(pacienteId);

        List<OdontogramaRegistroResponseDTO> piezas = odontogramaRepository
                .findEstadoActualPorPaciente(pacienteId)
                .stream()
                .map(OdontogramaRegistroResponseDTO::fromEntity)
                .sorted(Comparator.comparingInt(p -> (p.getPiezaFdi() != null ? p.getPiezaFdi() : 0)))
                .toList();

        return construirPdf(paciente, piezas);
    }

    private byte[] construirPdf(PacienteResumen paciente, List<OdontogramaRegistroResponseDTO> piezas) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font clinicFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(180, 83, 9));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
            Font sectionFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,  Color.BLACK);
            Font headerFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,  Color.WHITE);
            Font bodyFont     = FontFactory.getFont(FontFactory.HELVETICA,      9,  Color.BLACK);
            Font smallFont    = FontFactory.getFont(FontFactory.HELVETICA,      8,  Color.GRAY);

            Color headerBg    = new Color(51, 65, 85);
            Color porHacerBg  = new Color(254, 226, 226); // rojo suave
            Color realizadoBg = new Color(219, 234, 254); // azul suave

            // Cabecera
            Paragraph h1 = new Paragraph("CLÍNICA ODONTOLÓGICA ODONTOMAX", clinicFont);
            h1.setAlignment(Element.ALIGN_CENTER);
            doc.add(h1);

            Paragraph h2 = new Paragraph("ODONTOGRAMA FDI — ESTADO ACTUAL DE PIEZAS DENTALES", subtitleFont);
            h2.setAlignment(Element.ALIGN_CENTER);
            h2.setSpacingAfter(12);
            doc.add(h2);

            // Datos del paciente
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(14);

            PdfPCell cLeft = new PdfPCell();
            cLeft.setBorder(Rectangle.BOX);
            cLeft.setPadding(8);
            cLeft.addElement(new Paragraph("Paciente: " + nombreCompleto(paciente), sectionFont));
            if (paciente.dni() != null) {
                cLeft.addElement(new Paragraph("DNI: " + paciente.dni(), bodyFont));
            }
            if (paciente.fechaNacimiento() != null) {
                int edad = LocalDate.now(ZoneId.of("America/Lima")).getYear()
                        - paciente.fechaNacimiento().getYear();
                cLeft.addElement(new Paragraph("Edad: " + edad + " años", bodyFont));
            }

            PdfPCell cRight = new PdfPCell();
            cRight.setBorder(Rectangle.BOX);
            cRight.setPadding(8);
            cRight.addElement(new Paragraph("Fecha de impresión: " + LocalDate.now(ZoneId.of("America/Lima")), bodyFont));
            cRight.addElement(new Paragraph("Total de piezas registradas: " + piezas.size(), bodyFont));

            infoTable.addCell(cLeft);
            infoTable.addCell(cRight);
            doc.add(infoTable);

            // Leyenda
            Paragraph leyenda = new Paragraph(
                    "LEYENDA:  POR HACER = fondo rojo (diagnóstico pendiente de tratamiento)   |   REALIZADO = fondo azul (tratamiento completado)",
                    smallFont);
            leyenda.setSpacingAfter(10);
            doc.add(leyenda);

            if (piezas.isEmpty()) {
                doc.add(new Paragraph("Sin registros en el odontograma para este paciente.", bodyFont));
            } else {
                // Tabla de piezas
                PdfPTable tabla = new PdfPTable(5);
                tabla.setWidthPercentage(100);
                tabla.setWidths(new float[]{1.2f, 2.5f, 1.8f, 2.5f, 3.0f});
                tabla.setSpacingAfter(20);

                String[] cols = {"Pieza FDI", "Estado", "Dentición", "Caras afectadas", "Observación"};
                for (String col : cols) {
                    PdfPCell th = new PdfPCell(new Phrase(col, headerFont));
                    th.setBackgroundColor(headerBg);
                    th.setPadding(6);
                    th.setHorizontalAlignment(Element.ALIGN_CENTER);
                    tabla.addCell(th);
                }

                for (OdontogramaRegistroResponseDTO p : piezas) {
                    boolean porHacer = "por_hacer".equalsIgnoreCase(p.getEstado());
                    Color rowBg = porHacer ? porHacerBg : realizadoBg;
                    String estadoLabel = porHacer ? "POR HACER" : "REALIZADO";
                    String denticion = denticionDePieza(p.getPiezaFdi());
                    String caras = p.getCaras() != null && !p.getCaras().isEmpty()
                            ? String.join(", ", p.getCaras())
                            : "—";
                    String obs = p.getObservacion() != null ? p.getObservacion() : "—";

                    tabla.addCell(celdaColoreada(String.valueOf(p.getPiezaFdi()), bodyFont, rowBg, Element.ALIGN_CENTER));
                    tabla.addCell(celdaColoreada(estadoLabel, sectionFont, rowBg, Element.ALIGN_CENTER));
                    tabla.addCell(celdaColoreada(denticion, bodyFont, rowBg, Element.ALIGN_CENTER));
                    tabla.addCell(celdaColoreada(caras, bodyFont, rowBg, Element.ALIGN_LEFT));
                    tabla.addCell(celdaColoreada(obs, bodyFont, rowBg, Element.ALIGN_LEFT));
                }

                doc.add(tabla);
            }

            // Pie de página / firma
            Paragraph pie = new Paragraph(
                    "Este documento es una impresión del estado actual del odontograma registrado en el sistema. " +
                    "No reemplaza la historia clínica original.", smallFont);
            pie.setAlignment(Element.ALIGN_CENTER);
            doc.add(pie);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el PDF del odontograma: " + e.getMessage(), e);
        }
    }

    private PdfPCell celdaColoreada(String texto, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private String nombreCompleto(PacienteResumen p) {
        StringBuilder sb = new StringBuilder();
        if (p.nombres() != null) sb.append(p.nombres());
        if (p.apellidos() != null) sb.append(" ").append(p.apellidos());
        return sb.toString().trim();
    }

    private String denticionDePieza(Integer fdi) {
        if (fdi == null) return "—";
        if (fdi >= 51 && fdi <= 85) return "DECIDUA";
        return "PERMANENTE";
    }
}
