package dpf.sp.gpinf.indexer.parsers;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.apache.tika.fork.EmbeddedDocumentParser;
import org.apache.tika.fork.*;

public class PackageParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    private static int getVersion() {
        String version = System.getProperty("java.version");
        if(version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if(dot != -1) { version = version.substring(0, dot); }
        } return Integer.parseInt(version);
    }
    
    @Test
    public void testPackageParserZipParsing() throws IOException, SAXException, TikaException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockZip.zip");
        parser.getSupportedTypes(recursingContext);
        ClassLoader loader = new ClassLoader() {};
        EmbeddedDocumentParser eparser = new EmbeddedDocumentParser(recursingContext);
        EmbeddedDocumentExtractorProxy edep = new EmbeddedDocumentExtractorProxy(getVersion());
        EmbeddedDocumentExtractorResource eder = new EmbeddedDocumentExtractorResource(edep, handler);
        ExternalParsingParserFactory eppf = new ExternalParsingParserFactory(null);
        ForkParser fp = new ForkParser(loader);
        ForkParser2 fp2 = new ForkParser2();
        ParseContextProxy pcp = new ParseContextProxy();
        fp.close();
        parser.parse(stream, handler, metadata, recursingContext);
        eparser.parseEmbedded(stream, handler, metadata, false);
        edep.parseEmbedded(stream, handler, metadata, false);
        System.out.println(fp.toString() + fp2.toString() + pcp.toString() + eder.toString() + eppf.toString());
        String content = handler.toString();
        
        assertTrue(content.contains("mockdoc1.docx\n"
                + "· Lorem ipsum dolor sit amet, consectetur adipiscing elit."));
        assertTrue(content.contains("mockdoc2.docx\n"
                + "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean nunc augue, tincidunt eget mi a, euismod ultricies augue."));
        assertTrue(content.contains("mocksheets1.xlsx\nPlan1"));
        assertTrue(content.contains("mocksheets2.xlsx\nPlan1"));
    }
    
    @Test
    public void testPackageParserZipEmbedded() throws IOException, SAXException, TikaException, ParseException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockZip.zip");
        parser.getSupportedTypes(trackingContext);
        parser.parse(stream, handler, metadata, trackingContext);
        
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));

        
        assertEquals(10, tracker.filenames.size());
        assertEquals(10, tracker.modifieddate.size());
        assertEquals(10, tracker.itensmd5.size());
      
        assertEquals("mockdoc1.docx", tracker.filenames.get(0));
        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
        if(getVersion() < 9)
            assertEquals("08/04/2021 17:03:32", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("8 de abr de 2021 17:03:32", df.format(date));
        if(getVersion() >= 12)
            assertEquals("8 de abr. de 2021 17:03:32", df.format(date));
        //assertEquals("2021-04-08", tracker.modifieddate.get(0).substring(0,10));
        //assertEquals("17:03:32", tracker.modifieddate.get(0).substring(11,19));
        assertEquals("DF8000908EE52C996D5D151AC8DB730C", tracker.itensmd5.get(0));
        assertEquals("false", tracker.isfolder.get(0));
        
        assertEquals("mockdoc2.docx", tracker.filenames.get(1));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
        if(getVersion() < 9)
            assertEquals("08/04/2021 17:03:54", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("8 de abr de 2021 17:03:54", df.format(date));
        if(getVersion() >= 12)
            assertEquals("8 de abr. de 2021 17:03:54", df.format(date));
        //assertEquals("2021-04-08", tracker.modifieddate.get(1).substring(0,10));
        //assertEquals("17:03:54", tracker.modifieddate.get(1).substring(11,19));
        assertEquals("9BB76549BF676C633BE94B9608FA5DAE", tracker.itensmd5.get(1));
        assertEquals("false", tracker.isfolder.get(1));

        assertEquals("mocksheets1.xlsx", tracker.filenames.get(2));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
        if(getVersion() < 9)
            assertEquals("08/04/2021 17:06:14", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("8 de abr de 2021 17:06:14", df.format(date));
        if(getVersion() >= 12)
            assertEquals("8 de abr. de 2021 17:06:14", df.format(date));
        //assertEquals("2021-04-08", tracker.modifieddate.get(2).substring(0,10));
        //assertEquals("17:06:14", tracker.modifieddate.get(2).substring(11,19));
        assertEquals("5C1ADD743E6E1389EC8CB1AF78E79202", tracker.itensmd5.get(2));
        assertEquals("false", tracker.isfolder.get(2));
        
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(3));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(3));
        if(getVersion() < 9)
            assertEquals("08/04/2021 17:07:16", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("8 de abr de 2021 17:07:16", df.format(date));
        if(getVersion() >= 12)
            assertEquals("8 de abr. de 2021 17:07:16", df.format(date));
        //assertEquals("2021-04-08", tracker.modifieddate.get(3).substring(0,10));
        //assertEquals("17:07:16", tracker.modifieddate.get(3).substring(11,19));
        assertEquals("4375CBF2CF4920CFE0AAA23C4B1A1744", tracker.itensmd5.get(3));
        assertEquals("false", tracker.isfolder.get(3));
        
        assertEquals("mocktext1.txt", tracker.filenames.get(4));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(4));
        if(getVersion() < 9)
            assertEquals("08/04/2021 17:02:00", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("8 de abr de 2021 17:02:00", df.format(date));
        if(getVersion() >= 12)
            assertEquals("8 de abr. de 2021 17:02:00", df.format(date));
        //assertEquals("2021-04-08", tracker.modifieddate.get(4).substring(0,10));
        //assertEquals("17:02:00", tracker.modifieddate.get(4).substring(11,19));
        assertEquals("761D9CBE8BE4BDB94A8962074053CA85", tracker.itensmd5.get(4));
        assertEquals("false", tracker.isfolder.get(4));
        
        assertEquals("mocktext2.txt", tracker.filenames.get(5));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(5));
        if(getVersion() < 9)
            assertEquals("08/04/2021 17:02:14", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("8 de abr de 2021 17:02:14", df.format(date));
        if(getVersion() >= 12)
            assertEquals("8 de abr. de 2021 17:02:14", df.format(date));
        //assertEquals("2021-04-08", tracker.modifieddate.get(5).substring(0,10));
        //assertEquals("17:02:14", tracker.modifieddate.get(5).substring(11,19));
        assertEquals("17F87B5AF0D45F02FFCAF36093BD1ED1", tracker.itensmd5.get(5));
        assertEquals("false", tracker.isfolder.get(5));
        
        assertEquals("mockfolder", tracker.filenames.get(6));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(6));
        if(getVersion() < 9)
            assertEquals("09/04/2021 13:19:20", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 13:19:20", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 13:19:20", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(6).substring(0,10));
        //assertEquals("13:19:20", tracker.modifieddate.get(6).substring(11,19));
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(6));
        assertEquals("true", tracker.isfolder.get(6));
        
        assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(7));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(7));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:28", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:28", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:28", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(7).substring(0,10));
        //assertEquals("12:26:28", tracker.modifieddate.get(7).substring(11,19));
        assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(7));
        assertEquals("false", tracker.isfolder.get(7));
        
        assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(8));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(8));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:16", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:16", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:16", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(8).substring(0,10));
        //assertEquals("12:26:16", tracker.modifieddate.get(8).substring(11,19));
        assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(8));
        assertEquals("false", tracker.isfolder.get(8));

        assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(9));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(9));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:00", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:00", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:00", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(9).substring(0,10));
        //assertEquals("12:26:00", tracker.modifieddate.get(9).substring(11,19));
        assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(9));
        assertEquals("false", tracker.isfolder.get(9));
        
    }
    

    @Test
    public void testPackageParserTarParsing() throws IOException, SAXException, TikaException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockTar.tar");
        parser.getSupportedTypes(recursingContext);
        parser.parse(stream, handler, metadata, recursingContext);
        
        String content = handler.toString();
        
        assertTrue(content.contains("mockdoc1.docx\nMockdoc1:"));
        assertTrue(content.contains("mockdoc2.docx\nMockdoc2:"));
        assertTrue(content.contains("mockfolder\n\nmockfolder/mockdoc5.docx"));
        assertTrue(content.contains("mockfolder/mocksheets5.xlsx\nPlan1\n"));
    }
    
    @Test
    public void testPackageParserTarEmbedded() throws IOException, SAXException, TikaException, ParseException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockTar.tar");
        parser.getSupportedTypes(trackingContext);
        parser.parse(stream, handler, metadata, trackingContext);
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));

        
        assertEquals(10, tracker.filenames.size());
        assertEquals(10, tracker.modifieddate.size());
        assertEquals(10, tracker.itensmd5.size());
      
        assertEquals("mockdoc1.docx", tracker.filenames.get(0));
        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:42", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:42", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:42", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(0).substring(0,10));
        //assertEquals("12:25:42", tracker.modifieddate.get(0).substring(11,19));
        assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));
        assertEquals("false", tracker.isfolder.get(0));
        
        assertEquals("mockdoc2.docx", tracker.filenames.get(1));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:30", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:30", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:30", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
        //assertEquals("12:25:30", tracker.modifieddate.get(1).substring(11,19));
        assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));
        assertEquals("false", tracker.isfolder.get(1));

        assertEquals("mockfolder", tracker.filenames.get(2));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
        if(getVersion() < 9)
            assertEquals("09/04/2021 13:19:21", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 13:19:21", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 13:19:21", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(2).substring(0,10));
        //assertEquals("13:19:21", tracker.modifieddate.get(2).substring(11,19));
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(2));
        assertEquals("true", tracker.isfolder.get(2));
        
        assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(3));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(3));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:28", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:28", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:28", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
        //assertEquals("12:26:28", tracker.modifieddate.get(3).substring(11,19));
        assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(3));
        assertEquals("false", tracker.isfolder.get(3));
        
        assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(4));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(4));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:17", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:17", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:17", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
        //assertEquals("12:26:17", tracker.modifieddate.get(4).substring(11,19));
        assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(4));
        assertEquals("false", tracker.isfolder.get(4));
        
        assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(5));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(5));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:01", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:01", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:01", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
        //assertEquals("12:26:01", tracker.modifieddate.get(5).substring(11,19));
        assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(5));
        assertEquals("false", tracker.isfolder.get(5));
        
        assertEquals("mocksheets1.xlsx", tracker.filenames.get(6));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(6));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:22", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:22", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:22", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(6).substring(0,10));
        //assertEquals("12:24:22", tracker.modifieddate.get(6).substring(11,19));
        assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(6));
        assertEquals("false", tracker.isfolder.get(6));
        
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(7));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(7));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:12", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:12", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:12", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(7).substring(0,10));
        //assertEquals("12:24:12", tracker.modifieddate.get(7).substring(11,19));
        assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(7));
        assertEquals("false", tracker.isfolder.get(7));
        
        assertEquals("mocktext1.txt", tracker.filenames.get(8));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(8));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:23:02", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:23:02", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:23:02", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(8).substring(0,10));
        //assertEquals("12:23:02", tracker.modifieddate.get(8).substring(11,19));
        assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(8));
        assertEquals("false", tracker.isfolder.get(8));

        assertEquals("mocktext2.txt", tracker.filenames.get(9));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(9));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:22:54", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:22:54", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:22:54", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(9).substring(0,10));
        //assertEquals("12:22:54", tracker.modifieddate.get(9).substring(11,19));
        assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(9));
        assertEquals("false", tracker.isfolder.get(9));
        
    }
    
    @Test
    public void testPackageParser7zParsing() throws IOException, SAXException, TikaException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mock7z_2.7z");
        parser.getSupportedTypes(recursingContext);
        parser.parse(stream, handler, metadata, recursingContext);
        
        String content = handler.toString();
        
        assertTrue(content.contains("mockdoc1.docx\nMockdoc1:"));
        assertTrue(content.contains("mockdoc2.docx\nMockdoc2:"));
        assertTrue(content.contains("mockfolder/mockdoc5.docx\nMockdoc5/folder"));
        assertTrue(content.contains("mockfolder/mocksheets5.xlsx\nPlan1\n"));
    }
    
    @Test
    public void testPackageParser7zEmbedded() throws IOException, SAXException, TikaException, ParseException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mock7z_2.7z");
        parser.getSupportedTypes(trackingContext);
        parser.parse(stream, handler, metadata, trackingContext);
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));

        
        assertEquals(11, tracker.filenames.size());
        assertEquals(11, tracker.modifieddate.size());
        assertEquals(11, tracker.itensmd5.size());
      
        assertEquals("mockfolder", tracker.filenames.get(0));
        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
        if(getVersion() < 9)
            assertEquals("09/04/2021 13:19:21", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 13:19:21", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 13:19:21", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(0).substring(0,10));
        //assertEquals("13:19:21", tracker.modifieddate.get(0).substring(11,19));
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(0));
        assertEquals("true", tracker.isfolder.get(0));
        
        assertEquals("mockdoc1.docx", tracker.filenames.get(1));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:42", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:42", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:42", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
        //assertEquals("12:25:42", tracker.modifieddate.get(1).substring(11,19));
        assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(1));
        assertEquals("false", tracker.isfolder.get(1));

        assertEquals("mockdoc2.docx", tracker.filenames.get(2));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:30", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:30", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:30", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(2).substring(0,10));
        //assertEquals("12:25:30", tracker.modifieddate.get(2).substring(11,19));
        assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(2));
        assertEquals("false", tracker.isfolder.get(2));
        
        assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(3));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(3));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:28", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:28", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:28", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
        //assertEquals("12:26:28", tracker.modifieddate.get(3).substring(11,19));
        assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(3));
        assertEquals("false", tracker.isfolder.get(3));
        
        assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(4));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(4));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:17", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:17", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:17", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
        //assertEquals("12:26:17", tracker.modifieddate.get(4).substring(11,19));
        assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(4));
        assertEquals("false", tracker.isfolder.get(4));
        
        assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(5));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(5));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:26:01", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:26:01", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:26:01", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
        //assertEquals("12:26:01", tracker.modifieddate.get(5).substring(11,19));
        assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(5));
        assertEquals("false", tracker.isfolder.get(5));
        
        assertEquals("mockrar.zip", tracker.filenames.get(6));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(6));
        if(getVersion() < 9)
            assertEquals("08/04/2021 18:13:51", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("8 de abr de 2021 18:13:51", df.format(date));
        if(getVersion() >= 12)
            assertEquals("8 de abr. de 2021 18:13:51", df.format(date));
        //assertEquals("2021-04-08", tracker.modifieddate.get(6).substring(0,10));
        //assertEquals("18:13:51", tracker.modifieddate.get(6).substring(11,19));
        assertEquals("0A6931F7799E5A2D28AB5A22E6C84088", tracker.itensmd5.get(6));
        assertEquals("false", tracker.isfolder.get(6));
        
        assertEquals("mocksheets1.xlsx", tracker.filenames.get(7));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(7));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:22", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:22", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:22", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(7).substring(0,10));
        //assertEquals("12:24:22", tracker.modifieddate.get(7).substring(11,19));
        assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(7));
        assertEquals("false", tracker.isfolder.get(7));
        
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(8));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(8));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:12", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:12", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:12", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(8).substring(0,10));
        //assertEquals("12:24:12", tracker.modifieddate.get(8).substring(11,19));
        assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(8));
        assertEquals("false", tracker.isfolder.get(8));

        assertEquals("mocktext1.txt", tracker.filenames.get(9));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(9));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:23:02", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:23:02", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:23:02", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(9).substring(0,10));
        //assertEquals("12:23:02", tracker.modifieddate.get(9).substring(11,19));
        assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(9));
        assertEquals("false", tracker.isfolder.get(9));
        
        assertEquals("mocktext2.txt", tracker.filenames.get(10));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(10));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:22:54", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:22:54", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:22:54", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(10).substring(0,10));
        //assertEquals("12:22:54", tracker.modifieddate.get(10).substring(11,19));
        assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(10));
        assertEquals("false", tracker.isfolder.get(10));
        
        
    }
    
    @Test
    public void testPackageParserJarParsing() throws IOException, SAXException, TikaException{

        PackageParser parser = new PackageParser();
        Metadata metadata = spy(new Metadata());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_jar.jar");
        parser.getSupportedTypes(recursingContext);
        when(metadata.get(Metadata.CONTENT_TYPE)).thenReturn("application/java");
        parser.parse(stream, handler, metadata, recursingContext);
        
        String content = handler.toString();
        

        assertTrue(content.contains("META-INF"));
        assertTrue(content.contains("Manifest-Version: 1.0"));
        assertTrue(content.contains("Created-By: 1.8.0_281 (Oracle Corporation)"));
        assertTrue(content.contains("testJar.class"));
        assertTrue(content.contains("public void testJar();"));
        assertTrue(content.contains("static void insereMoeda(char, int[]);"));
        assertTrue(content.contains("static void trocaAB(int[]);"));
        
    }
    
    @Test
    public void testPackageParserJarEmbedded() throws IOException, SAXException, TikaException, ParseException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_jar.jar");
        parser.getSupportedTypes(trackingContext);
        parser.parse(stream, handler, metadata, trackingContext);
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));

        
        assertEquals(3, tracker.filenames.size());
        assertEquals(3, tracker.modifieddate.size());
        assertEquals(3, tracker.itensmd5.size());
      
        assertEquals("META-INF", tracker.filenames.get(0));
        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
        if(getVersion() < 9)
            assertEquals("30/04/2021 18:26:20", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("30 de abr de 2021 18:26:20", df.format(date));
        if(getVersion() >= 12)
            assertEquals("30 de abr. de 2021 18:26:20", df.format(date));
        //assertEquals("2021-04-30", tracker.modifieddate.get(0).substring(0,10));
        //assertEquals("18:26:20", tracker.modifieddate.get(0).substring(11,19));
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(0));
        assertEquals("true", tracker.isfolder.get(0));
        
        assertEquals("META-INF/MANIFEST.MF", tracker.filenames.get(1));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
        if(getVersion() < 9)
            assertEquals("30/04/2021 18:26:20", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("30 de abr de 2021 18:26:20", df.format(date));
        if(getVersion() >= 12)
            assertEquals("30 de abr. de 2021 18:26:20", df.format(date));
        //assertEquals("2021-04-30", tracker.modifieddate.get(1).substring(0,10));
        //assertEquals("18:26:20", tracker.modifieddate.get(1).substring(11,19));
        assertEquals("F32E26890EACA8D3D39153D2E2B3EC2F", tracker.itensmd5.get(1));
        assertEquals("false", tracker.isfolder.get(1));

        assertEquals("testJar.class", tracker.filenames.get(2));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
        if(getVersion() < 9)
            assertEquals("30/04/2021 18:24:42", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("30 de abr de 2021 18:24:42", df.format(date));
        if(getVersion() >= 12)
            assertEquals("30 de abr. de 2021 18:24:42", df.format(date));
        //assertEquals("2021-04-30", tracker.modifieddate.get(2).substring(0,10));
        //assertEquals("18:24:42", tracker.modifieddate.get(2).substring(11,19));
        assertEquals("B402684EEDD7BAA0E6D61CCF51498BE4", tracker.itensmd5.get(2));
        assertEquals("false", tracker.isfolder.get(2));
       
    }

    @Test
    public void testPackageParserArjParsing() throws IOException, SAXException, TikaException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockArc.arj");
        parser.getSupportedTypes(recursingContext);
        parser.parse(stream, handler, metadata, recursingContext);
        String hts = handler.toString();
        assertTrue(hts.contains("mockdoc1.docx"));
        assertTrue(hts.contains("mockdoc2.docx"));
        assertTrue(hts.contains("mocksheets1.xlsx"));
        assertTrue(hts.contains("mocksheets2.xlsx"));
        assertTrue(hts.contains("mocktext1.txt"));
        assertTrue(hts.contains("mocktext2.txt"));
       
    }
    
    @Test
    public void testPackageParserArjEmbedded() throws IOException, SAXException, TikaException, ParseException{

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockArc.arj");
        parser.getSupportedTypes(trackingContext);
        parser.parse(stream, handler, metadata, trackingContext);
        assertEquals(6, tracker.filenames.size());
        assertEquals(6, tracker.modifieddate.size());
        assertEquals(6, tracker.itensmd5.size());
        
        int style = DateFormat.MEDIUM;
        DateFormat df;
        df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));

        
        assertEquals("mockdoc1.docx", tracker.filenames.get(0));
        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:42", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:42", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:42", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(0).substring(0,10));
        assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));
        
        assertEquals("mockdoc2.docx", tracker.filenames.get(1));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:25:30", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:25:30", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:25:30", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
        assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));
        
        assertEquals("mocksheets1.xlsx", tracker.filenames.get(2));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:22", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:22", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:22", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(2).substring(0,10));
        assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(2));
        
        assertEquals("mocksheets2.xlsx", tracker.filenames.get(3));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(3));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:24:12", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:24:12", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:24:12", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
        assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(3));
        
        assertEquals("mocktext1.txt", tracker.filenames.get(4));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(4));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:23:02", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:23:02", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:23:02", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
        assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(4));
        
        assertEquals("mocktext2.txt", tracker.filenames.get(5));
        date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(5));
        if(getVersion() < 9)
            assertEquals("09/04/2021 12:22:54", df.format(date));
        if(getVersion() >= 9 && getVersion() < 12)
            assertEquals("9 de abr de 2021 12:22:54", df.format(date));
        if(getVersion() >= 12)
            assertEquals("9 de abr. de 2021 12:22:54", df.format(date));
        //assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
        assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(5));
      
       
    }
    
    
  @Test
  public void testPackageParserArParsing() throws IOException, SAXException, TikaException{

      PackageParser parser = new PackageParser();
      Metadata metadata = new Metadata();
      ContentHandler handler = new BodyContentHandler();
      InputStream stream = getStream("test-files/test_mockArc.ar");
      parser.getSupportedTypes(recursingContext);
      parser.parse(stream, handler, metadata, recursingContext);
      String hts = handler.toString();
      assertTrue(hts.contains("mockdoc1.docx"));
      assertTrue(hts.contains("mockdoc2.docx"));
      assertTrue(hts.contains("mocksheets1.xlsx"));
      assertTrue(hts.contains("mocksheets2.xlsx"));
      assertTrue(hts.contains("mocktext1.txt"));
      assertTrue(hts.contains("mocktext2.txt"));
     
  }
  
  @Test
  public void testPackageParserArEmbedded() throws IOException, SAXException, TikaException, ParseException{

      PackageParser parser = new PackageParser();
      Metadata metadata = new Metadata();
      ContentHandler handler = new BodyContentHandler();
      InputStream stream = getStream("test-files/test_mockArc.ar");
      parser.getSupportedTypes(trackingContext);
      parser.parse(stream, handler, metadata, trackingContext);
      assertEquals(6, tracker.filenames.size());
      assertEquals(6, tracker.modifieddate.size());
      assertEquals(6, tracker.itensmd5.size());
      
      int style = DateFormat.MEDIUM;
      DateFormat df;
      df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));
    
    
    assertEquals("mockdoc1.docx", tracker.filenames.get(0));
    Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:25:42", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:25:42", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:25:42", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(0).substring(0,10));
    assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));
    
    assertEquals("mockdoc2.docx", tracker.filenames.get(1));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:25:30", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:25:30", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:25:30", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
    assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));
    
    assertEquals("mocksheets1.xlsx", tracker.filenames.get(2));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:24:22", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:24:22", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:24:22", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(2).substring(0,10));
    assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(2));
    
    assertEquals("mocksheets2.xlsx", tracker.filenames.get(3));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(3));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:24:12", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:24:12", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:24:12", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
    assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(3));
    
    assertEquals("mocktext1.txt", tracker.filenames.get(4));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(4));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:23:02", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:23:02", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:23:02", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
    assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(4));
    
    assertEquals("mocktext2.txt", tracker.filenames.get(5));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(5));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:22:54", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:22:54", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:22:54", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
    assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(5));
    
     
  }

  @Test
  public void testPackageParserCpioParsing() throws IOException, SAXException, TikaException{

      PackageParser parser = new PackageParser();
      Metadata metadata = new Metadata();
      ContentHandler handler = new BodyContentHandler();
      InputStream stream = getStream("test-files/test_cpio.cpio");
      parser.getSupportedTypes(recursingContext);
      parser.parse(stream, handler, metadata, recursingContext);
      String hts = handler.toString();

      assertTrue(hts.contains("mockdoc1.docx"));
      assertTrue(hts.contains("mockdoc2.docx"));
      assertTrue(hts.contains("mocksheets1.xlsx"));
      assertTrue(hts.contains("mocksheets2.xlsx"));
      assertTrue(hts.contains("mocktext1.txt"));
      assertTrue(hts.contains("mocktext2.txt"));
      
  }
  

  @Test
  public void testPackageParserCpioEmbedded() throws IOException, SAXException, TikaException, ParseException{

      PackageParser parser = new PackageParser();
      Metadata metadata = new Metadata();
      ContentHandler handler = new BodyContentHandler();
      InputStream stream = getStream("test-files/test_cpio.cpio");
      parser.getSupportedTypes(trackingContext);
      parser.parse(stream, handler, metadata, trackingContext);
      assertEquals(8, tracker.filenames.size());
      assertEquals(8, tracker.modifieddate.size());
      assertEquals(8, tracker.itensmd5.size());
      
      int style = DateFormat.MEDIUM;
      DateFormat df;
      df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));

    
    
    assertEquals("mockdoc1.docx", tracker.filenames.get(0));
    Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
    if(getVersion() < 9)
        assertEquals("04/05/2021 17:07:20", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("4 de mai de 2021 17:07:20", df.format(date));
    if(getVersion() >= 12)
        assertEquals("4 de mai. de 2021 17:07:20", df.format(date));
    //assertEquals("2021-05-04", tracker.modifieddate.get(0).substring(0,10));
    assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));
    
    assertEquals("mockdoc2.docx", tracker.filenames.get(1));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:25:30", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:25:30", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:25:30", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(1).substring(0,10));
    assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));
    
    assertEquals("mockfolder", tracker.filenames.get(2));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(2));
    if(getVersion() < 9)
        assertEquals("04/05/2021 17:08:07", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("4 de mai de 2021 17:08:07", df.format(date));
    if(getVersion() >= 12)
        assertEquals("4 de mai. de 2021 17:08:07", df.format(date));
    //assertEquals("2021-05-04", tracker.modifieddate.get(2).substring(0,10));
    assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(2));
    assertEquals("true", tracker.isfolder.get(2));
    
    assertEquals("mocksheets1.xlsx", tracker.filenames.get(3));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(3));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:24:22", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:24:22", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:24:22", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(3).substring(0,10));
    assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(3));
    
    assertEquals("mocksheets2.xlsx", tracker.filenames.get(4));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(4));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:24:12", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:24:12", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:24:12", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(4).substring(0,10));
    assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(4));
    
    assertEquals("mocktext1.txt", tracker.filenames.get(5));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(5));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:23:02", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:23:02", df.format(date));
    if(getVersion() >= 12)
        assertEquals("9 de abr. de 2021 12:23:02", df.format(date));
    //assertEquals("2021-04-09", tracker.modifieddate.get(5).substring(0,10));
    assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(5));
    
    assertEquals("mocktext2.txt", tracker.filenames.get(6));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(6));
    if(getVersion() < 9)
        assertEquals("09/04/2021 12:22:54", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("9 de abr de 2021 12:22:54", df.format(date));
    if(getVersion() >= 12)
    //assertEquals("2021-04-09", tracker.modifieddate.get(6).substring(0,10));
    assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(6));

    assertEquals("test.cpio", tracker.filenames.get(7));
    date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(7));
    if(getVersion() < 9)
        assertEquals("04/05/2021 17:08:20", df.format(date));
    if(getVersion() >= 9 && getVersion() < 12)
        assertEquals("4 de mai de 2021 17:08:20", df.format(date));
    if(getVersion() >= 12)
        assertEquals("4 de mai. de 2021 17:08:20", df.format(date));
    //assertEquals("2021-05-04", tracker.modifieddate.get(7).substring(0,10));
    assertEquals("BA61B70D7C45946AC670D39080C1DD42", tracker.itensmd5.get(7));
     
  }

  @Test
  public void testPackageParserOoxmlParsing() throws IOException, SAXException, TikaException{

      PackageParser parser = new PackageParser();
      Metadata metadata = new Metadata();
      ContentHandler handler = new BodyContentHandler();
      InputStream stream = getStream("test-files/test_mockDoc1.docx");
      parser.getSupportedTypes(recursingContext);
      parser.parse(stream, handler, metadata, recursingContext);
      String hts = handler.toString();
      assertTrue(hts.contains("Mockdoc1:"));
      
  }
  
  @Test
  public void testPackageParserOoxmlEmbedded() throws IOException, SAXException, TikaException, ParseException{

      PackageParser parser = new PackageParser();
      Metadata metadata = new Metadata();
      ContentHandler handler = new BodyContentHandler();
      InputStream stream = getStream("test-files/test_mockDoc1.docx");
      parser.getSupportedTypes(trackingContext);
      parser.parse(stream, handler, metadata, trackingContext);
      assertEquals(17, tracker.filenames.size());
      assertEquals(12, tracker.modifieddate.size());
      assertEquals(17, tracker.itensmd5.size());
      assertEquals(22, tracker.isfolder.size());
      
      int style = DateFormat.MEDIUM;
      DateFormat df;
      df = DateFormat.getDateTimeInstance(style, style, new Locale("pt", "BR"));


      assertEquals("[Content_Types].xml", tracker.filenames.get(0));
      Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(0));
      if(getVersion() < 9)
          assertEquals("01/01/1980 03:00:00", df.format(date));
      if(getVersion() >= 9 && getVersion() < 12)
          assertEquals("1 de jan de 1980 03:00:00", df.format(date));
      if(getVersion() >= 12)
          assertEquals("1 de jan. de 1980 03:00:00", df.format(date));
      //assertEquals("1980-01-01", tracker.modifieddate.get(0).substring(0,10));
      assertEquals("A7CBC749C317FA432BA2DB5A47131123", tracker.itensmd5.get(0));
      assertEquals("false", tracker.isfolder.get(0));
      
      assertEquals("_rels", tracker.filenames.get(1));
      date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(tracker.modifieddate.get(1));
      if(getVersion() < 9)
          assertEquals("01/01/1980 03:00:00", df.format(date));
      if(getVersion() >= 9 && getVersion() < 12)
          assertEquals("1 de jan de 1980 03:00:00", df.format(date));
      if(getVersion() >= 12)
          assertEquals("1 de jan. de 1980 03:00:00", df.format(date));
      //assertEquals("1980-01-01", tracker.modifieddate.get(1).substring(0,10));
      assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(1));
      assertEquals("true", tracker.isfolder.get(1));
      
      
     
  }
  

  

}
