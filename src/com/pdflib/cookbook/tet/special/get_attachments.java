package com.pdflib.cookbook.tet.special;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Extract the text from the document and recursively from all embedded PDF
 * attachments.
 * <p>
 * Required software: TET 3
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: get_attachments.java,v 1.3 2008/11/20 08:06:39 stm Exp $
 */
public class get_attachments
{
    /**
     * Global option list. The program expects the "resource" directory parallel
     * to the "java" directory.
     */
    static final String GLOBAL_OPTLIST = "searchpath={../resource/cmap "
        + "../resource/glyphlist ../input}";

    /**
     * Document specific option list.
     */
    static final String DOC_OPTLIST = "";

    /**
     * Page-specific option list.
     */
    static final String PAGE_OPTLIST = "granularity=page";

    /**
     * The encoding in which the output is sent to System.out. For running
     * the example in a Windows command window, you can set this for example to
     * "windows-1252" for getting Latin-1 output.
     */
    private static final String OUTPUT_ENCODING =
                            System.getProperty("file.encoding");
    
    /**
     * For printing to System.out in the encoding specified via OUTPUT_ENCODING.
     */
    private static PrintStream out;

    /**
     * Separator to emit after each chunk of text. This depends on the
     * application's needs; for granularity=word a space character may be
     * useful.
     */
    static final String SEPARATOR = "\n";

    /**
     * Extract text from a document for which a TET handle is already available.
     * 
     * @param tet
     *            The TET object
     * @param doc
     *            A valid TET document handle
     * 
     * @throws TETException
     *             An error occurred in the TET API
     */
    static void extract_text(TET tet, int doc) throws TETException {
        /*
         * Get number of pages in the document.
         */
        int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

        /* loop over pages */
        for (int pageno = 1; pageno <= n_pages; ++pageno) {
            String text;
            int page;

            page = tet.open_page(doc, pageno, PAGE_OPTLIST);

            if (page == -1) {
                System.err.println("Error " + tet.get_errnum() + " in  "
                        + tet.get_apiname() + "() on page " + pageno + ": "
                        + tet.get_errmsg());
                continue; /* try next page */
            }

            /*
             * Retrieve all text fragments; This loop is actually not required
             * for granularity=page, but must be used for other granularities.
             */
            while ((text = tet.get_text(page)) != null) {
                out.print(text); // print the retrieved text

                /* print a separator between chunks of text */
                out.print(SEPARATOR);
            }

            if (tet.get_errnum() != 0) {
                System.err.println("Error " + tet.get_errnum() + " in  "
                        + tet.get_apiname() + "() on page " + pageno + ": "
                        + tet.get_errmsg());
            }

            tet.close_page(page);
        }
    }

    /**
     * Open a named physical or virtual file, extract the text from it, search
     * for document or page attachments, and process these recursively. Either
     * filename must be supplied for physical files, or data+length from which a
     * virtual file will be created. The caller cannot create the PVF file since
     * we create a new TET object here in case an exception happens with the
     * embedded document - the caller can happily continue with his TET object
     * even in case of an exception here.
     * 
     * @param filename
     *            The filename for an input file on disk (can be null)
     * @param attachmentname
     *            The name of the attachment for displaying it to the user
     * @param data
     *            Data of a PDF document loaded in memory (can be null)
     * 
     * @return 0 if successful, otherwise a non-null code to be used as exit
     *         status
     */
    static int process_document(String filename, String attachmentname,
            byte[] data) {
        int retval = 0;
        TET tet = null;
        try {
            final String pvfname = "/pvf/attachment";

            tet = new TET();

            /*
             * Construct a PVF file if data instead of a filename was provided
             */
            if (filename == null || filename.length() == 0) {
                tet.create_pvf(pvfname, data, "");
                filename = pvfname;
            }

            tet.set_option(GLOBAL_OPTLIST);

            int doc = tet.open_document(filename, DOC_OPTLIST);
            if (doc == -1) {
                System.err.println("Error " + tet.get_errnum() + " in  "
                        + tet.get_apiname() + "() (source: attachment '"
                        + attachmentname + "'): " + tet.get_errmsg());

                retval = 5;
            }
            else {
                process_document(tet, doc);
            }

            /*
             * If there was no PVF file deleting it won't do any harm
             */
            tet.delete_pvf(pvfname);
        }
        catch (TETException e) {
            System.err.println("Error " + e.get_errnum() + " in  "
                    + e.get_apiname() + "() (source: attachment '" + attachmentname
                    + "'): " + e.get_errmsg());
            retval = 1;
        }
        finally {
            if (tet != null) {
                tet.delete();
            }
        }

        return retval;
    }

    /**
     * Process a single file.
     * 
     * @param tet
     *            The TET object
     * @param doc
     *            The TET document handle
     * 
     * @throws TETException
     *             An error occurred in the TET API.
     */
    private static void process_document(TET tet, int doc) throws TETException {
        String objtype;

        // -------------------- Extract the document's own page contents
        extract_text(tet, doc);

        // -------------------- Process all document-level file attachments

        // Get the number of document-level file attachments.
        int filecount = (int) tet.pcos_get_number(doc,
                "length:names/EmbeddedFiles");

        for (int file = 0; file < filecount; file++) {
            String attname;

            /*
             * fetch the name of the file attachment; check for Unicode file
             * name (a PDF 1.7 feature)
             */
            objtype = tet.pcos_get_string(doc, "type:names/EmbeddedFiles["
                    + file + "]/UF");

            if (objtype.equals("string")) {
                attname = tet.pcos_get_string(doc, "names/EmbeddedFiles["
                        + file + "]/UF");
            }
            else {
                objtype = tet.pcos_get_string(doc, "type:names/EmbeddedFiles["
                        + file + "]/F");

                if (objtype.equals("string")) {
                    attname = tet.pcos_get_string(doc, "names/EmbeddedFiles["
                            + file + "]/F");
                }
                else {
                    attname = "(unnamed)";
                }
            }
            /* fetch the contents of the file attachment and process it */
            objtype = tet.pcos_get_string(doc, "type:names/EmbeddedFiles["
                    + file + "]/EF/F");

            if (objtype.equals("stream")) {
                out.println("----- File attachment '" + attname + "':");
                byte attdata[] = tet.pcos_get_stream(doc, "",
                        "names/EmbeddedFiles[" + file + "]/EF/F");

                process_document(null, attname, attdata);
                out.println("----- End file attachment '" + attname + "'");
            }
        }

        // -------------------- Process all page-level file attachments

        int pagecount = (int) tet.pcos_get_number(doc, "length:pages");

        // Check all pages for annotations of type FileAttachment
        for (int page = 0; page < pagecount; page++) {
            int annotcount = (int) tet.pcos_get_number(doc, "length:pages["
                    + page + "]/Annots");

            for (int annot = 0; annot < annotcount; annot++) {
                String val;
                String attname;

                val = tet.pcos_get_string(doc, "pages[" + page + "]/Annots["
                        + annot + "]/Subtype");

                attname = "page " + (page + 1) + ", annotation " + (annot + 1);
                if (val.equals("FileAttachment")) {
                    String attpath = "pages[" + page + "]/Annots[" + annot
                            + "]/FS/EF/F";
                    /*
                     * fetch the contents of the attachment and process it
                     */
                    objtype = tet.pcos_get_string(doc, "type:" + attpath);

                    if (objtype.equals("stream")) {
                        out.println("----- Page level attachment '" + attname + "':");
                        byte attdata[] = tet.pcos_get_stream(doc, "", attpath);
                        process_document(null, attname, attdata);
                        out.println("----- End page level attachment '" + attname + "':");
                    }
                }
            }
        }

        tet.close_document(doc);
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        int ret = 0;

        System.out.println("Using output encoding \"" + OUTPUT_ENCODING + "\"");
        out = new PrintStream(System.out, true, OUTPUT_ENCODING);

        if (args.length != 1) {
            System.err.println("usage: get_attachments <infilename>");
            System.exit(2);
        }

        try {
            ret = process_document(args[0], args[0], null);
        }
        catch (Exception e) {
            e.printStackTrace();
            ret = 1;
        }

        System.exit(ret);
    }
}
