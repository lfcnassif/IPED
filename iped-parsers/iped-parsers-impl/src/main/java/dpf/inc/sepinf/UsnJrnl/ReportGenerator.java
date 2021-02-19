package dpf.inc.sepinf.UsnJrnl;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.tika.io.TemporaryResources;

public class ReportGenerator {

    private static final String NEW_ROW = "<TR>"; //$NON-NLS-1$
    private static final String CLOSE_ROW = "</TR>"; //$NON-NLS-1$

    private static final String[] cols = { "Offset", "FileName", "USN", "TimeStamp", "Reasons", "MTF Ref.",
            "MTF parent Ref", "File attr", "Source Info", "Security Id" };

    public final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$

    private static final String newCol(String value, String tag) {

        return "<" + tag + ">" + value + "</" + tag + ">";
    }

    private static final String newCol(String value) {
        return newCol(value, "TD");
    }

    public void startHTMLDocument(PrintWriter out) {
        out.println("<!DOCTYPE html>"); //$NON-NLS-1$
        out.println("<HTML>"); //$NON-NLS-1$
        out.println("<HEAD>"); //$NON-NLS-1$
        out.print("<meta charset=\"UTF-8\">"); //$NON-NLS-1$
        out.println("<style>\r\n" + "table {\r\n" + "  border-collapse: collapse;\r\n" + "  width: 100%;\r\n"
                + "  border: 1px solid #ddd;\r\n" + "}\r\n" + "\r\n" + "th, td {\r\n" + "  text-align: left;\r\n"
                + "  padding: 16px;\r\n" + "}\r\n" + "\r\n" + "tr:nth-child(even) {\r\n"
                + "  background-color: #f2f2f2;\r\n" + "}\r\n" + "</style>");
        out.println("</HEAD>"); //$NON-NLS-1$
        out.println("<BODY>"); //$NON-NLS-1$
        out.println("<h2>Usn Journal</h2>"); //$NON-NLS-1$
    }

    public void endHTMLDocument(PrintWriter out) {
        out.println("</BODY></HTML>"); //$NON-NLS-1$
    }

    public InputStream createHTMLReport(List<UsnJrnlEntry> entries) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8)); // $NON-NLS-1$

        startHTMLDocument(out);

        out.print("<table border=1>");

        out.print(NEW_ROW);
        for (String col : cols) {
            out.print(newCol(col, "TH"));
        }
        out.print(CLOSE_ROW);
        for (UsnJrnlEntry u : entries) {
            out.print(NEW_ROW);
            out.print(newCol(String.format("0x%016X", u.getOffset())));
            out.print(newCol(u.getFileName()));
            out.print(newCol(u.getUSN() + ""));
            out.print(newCol(timeFormat.format(u.getFileTime())));
            out.print(newCol(u.getReasons()));
            out.print(newCol("0x" + Util.byteArrayToHex(u.getMftRef())));
            out.print(newCol("0x" + Util.byteArrayToHex(u.getParentMftRef())));
            out.print(newCol(u.getHumanAttributes()));
            out.print(newCol(u.getSourceInformation() + ""));
            out.print(newCol(u.getSecurityId() + ""));
            out.print(CLOSE_ROW);
        }

        out.print("</table>");

        endHTMLDocument(out);

        out.close();
        return new ByteArrayInputStream(bout.toByteArray());

    }

    public InputStream createCSVReport(List<UsnJrnlEntry> entries, TemporaryResources tmp) throws IOException {
        Path path = tmp.createTempFile();
        try (Writer writer = Files.newBufferedWriter(path); PrintWriter out = new PrintWriter(writer);) {
            boolean first = true;
            for (String col : cols) {
                if (first) {
                    out.print(col);
                    first = false;
                } else {
                    out.print(";" + col);
                }
            }
            for (UsnJrnlEntry u : entries) {
                out.print("\n");
                out.print(String.format("0x%016X", u.getOffset()) + ";");
                out.print("\"" + u.getFileName() + "\";");
                out.print(u.getUSN() + ";");
                out.print(timeFormat.format(u.getFileTime()) + ";");
                out.print(u.getReasons() + ";");
                out.print("0x" + Util.byteArrayToHex(u.getMftRef()) + ";");
                out.print("0x" + Util.byteArrayToHex(u.getParentMftRef()) + ";");
                out.print(u.getHumanAttributes() + ";");
                out.print(u.getSourceInformation() + ";");
                out.print(u.getSecurityId());
            }
        }

        return new BufferedInputStream(Files.newInputStream(path));
    }

}