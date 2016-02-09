package com.pdflib.cookbook.tet.text;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Formatter;
import java.util.Locale;

import com.pdflib.TET;
import com.pdflib.TETException;

/**
 * Simple PDF glyph dumper based on PDFlib TET
 *
 * Required software: TET 5
 * <p>
 * Required data: PDF document
 * 
 * @version $Id: glyphinfo.java,v 1.1 2015/12/03 10:06:37 stm Exp $
 */
public class glyphinfo
{
    /**
     * Global option list
     */
    static final String globaloptlist = "searchpath={{../input} {../resource/cmap}}";
    
    /**
     * Document-specific option list
     */
    static final String docoptlist = "";
    
    /**
     * Page-specific option list
     */
    static final String pageoptlist = "granularity=word";

    private static void print_color_value(Formatter formatter, TET tet, int doc, int colorid) throws TETException
    {
	int colorinfo;
	String csname;			/* color space name */
	int i;

	/* We handle only the fill color, but ignore the stroke color.
	 * The stroke color can be retrieved analogously with the
	 * keyword "stroke".
	 */
	colorinfo = tet.get_color_info(doc, colorid, "usage=fill");
	if (colorinfo == -1)
	{
	    formatter.format(" (not available)");
	    return;
	}
	
	if (tet.colorspaceid == -1 && tet.patternid == -1)
	{
	    formatter.format(" (not filled)");
	    return;
	}

	formatter.format(" (");

	if (tet.patternid != -1)
	{
	    int patterntype =
		(int)tet.pcos_get_number(doc, "patterns[" + tet.patternid + "]/PatternType");

	    if (patterntype == 1)	/* Tiling pattern */
	    {
		int painttype =
		    (int) tet.pcos_get_number(doc, "patterns[" + tet.patternid + "]/PaintType");
		if (painttype == 1)
		{
		    formatter.format( "colored Pattern)");
		    return;
		}
		else if (painttype == 2)
		{
		    formatter.format( "uncolored Pattern, base color: ");
		    /* FALLTHROUGH to colorspaceid output */
		}
	    }
	    else if (patterntype == 2)	/* Shading pattern */
	    {
		int shadingtype =
		    (int) tet.pcos_get_number(doc,
			    "patterns[" + tet.patternid + "]/Shading/ShadingType");

		formatter.format("shading Pattern, ShadingType=%d)", shadingtype);
		return;
	    }
	}

	csname = tet.pcos_get_string(doc, "colorspaces[" + tet.colorspaceid + "]/name");

	formatter.format("%s", csname);

	/* Emit more details depending on the colorspace type */
	if (csname.equals( "ICCBased"))
	{
	    int iccprofileid;
	    String profilename;
	    String profilecs;
	    String errormessage;

	    iccprofileid = (int) tet.pcos_get_number(doc,
				    "colorspaces[" + tet.colorspaceid + "]/iccprofileid");

	    errormessage = tet.pcos_get_string(doc,
			    "iccprofiles[" + iccprofileid + "]/errormessage");

	    /* Check whether the embedded profile is damaged */
	    if (errormessage.equals(""))
	    {
		formatter.format(" (%s)", errormessage);
	    }
	    else
	    {
		profilename =
		    tet.pcos_get_string(doc,
			"iccprofiles[" + iccprofileid + "]/profilename");
		formatter.format( " '%s'", profilename);

		profilecs = tet.pcos_get_string(doc,
			"iccprofiles[" + iccprofileid + "]/profilecs");
		formatter.format(" '%s'", profilecs);
	    }
	}
	else if (csname.equals("Separation"))
	{
	    String colorantname =
		tet.pcos_get_string(doc, "colorspaces[" + tet.colorspaceid + "]/colorantname");
	    formatter.format(" '%s'", colorantname);
	}
	else if (csname.equals("DeviceN"))
	{
	    formatter.format( " ");

	    for (i=0; i < tet.components.length; i++)
	    {
		String colorantname =
		    tet.pcos_get_string(doc,
			"colorspaces[" + tet.colorspaceid + "]/colorantnames[" + i + "]");

		formatter.format( "%s", colorantname);

		if (i != tet.components.length-1)
		    formatter.format("/");
	    }
	}
	else if (csname.equals( "Indexed"))
	{
	    int baseid =
		(int) tet.pcos_get_number(doc, "colorspaces[" + tet.colorspaceid + "]/baseid" );

	    csname = tet.pcos_get_string(doc, "colorspaces[" + baseid + "]/name");

	    formatter.format( " %s", csname);

	}

	formatter.format( " ");
	for (i=0; i < tet.components.length; i++)
	{
	    formatter.format( "%g", tet.components[i]);

	    if (i != tet.components.length-1)
		formatter.format( "/");
	}
	formatter.format( ")");
    }

    
    public static void main (String argv[])
    {
        TET tet = null;
        
	try
        {
	    if (argv.length !=1)
            {
                throw new Exception("usage: glyphinfo <filename>");
            }

	    /* print UTF-8 BOM */
	    byte[] bom = new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF };
	    System.out.write(bom);

            Writer outfp = new BufferedWriter(
				    new OutputStreamWriter(System.out, "UTF-8"));

	    Formatter formatter = new Formatter(outfp, Locale.US);

            tet = new TET();

            tet.set_option(globaloptlist);

            int doc = tet.open_document(argv[0], docoptlist);

            if (doc == -1)
            {
                formatter.close();
                throw new Exception("Error " + tet.get_errnum() + "in "
                        + tet.get_apiname() + "(): " + tet.get_errmsg());
            }
            
            /* get number of pages in the document */
            int n_pages = (int) tet.pcos_get_number(doc, "length:pages");

            /* loop over pages in the document */
            for (int pageno = 1; pageno <= n_pages; ++pageno)
            {
                String text;
                int page;
		int previouscolor = -1;
		
		page = tet.open_page(doc, pageno, pageoptlist);

                if (page == -1)
                {
                    print_tet_error(tet, pageno);
                    continue; /* try next page */
                }

		/* Administrative information */
		formatter.format("\n[ Document: '" + 
		    tet.pcos_get_string(doc, "filename") + "' ]\n");

		formatter.format("[ Document options: '%s' ]\n", docoptlist);

		formatter.format("[ Page options: '%s' ]\n", pageoptlist);

		formatter.format("[ ----- Page %d ----- ]\n", pageno);


		/* Retrieve all text fragments */
		while ((text = tet.get_text(page)) != null)
		{
		    @SuppressWarnings("unused")
                    int ci;

		    /* print the retrieved text */
		    outfp.write("[" + text + "]\n");

		    /* Loop over all glyphs and print their details */
		    while ((ci = tet.get_char_info(page)) != -1)
		    {
			final String fontname;

			/* Fetch the font name with pCOS (based on its ID) */
			fontname = tet.pcos_get_string(doc,
				    "fonts[" + tet.fontid + "]/name");

			/* Print the character */
			formatter.format("U+%04X", tet.uv);

			/* ...and its UTF8 representation */
			formatter.format(" '%c'", tet.uv);

			/* Print font name, size, and position */
			formatter.format(" %s size=%.2f x=%.2f y=%.2f",
			    fontname, tet.fontsize, tet.x, tet.y);

			/* Print the color id */
			formatter.format(" colorid=%d", tet.colorid);

			/* Check wheater the text color changed */
			if (tet.colorid != previouscolor){
			    print_color_value(formatter, tet, doc, tet.colorid);
			    previouscolor = tet.colorid;
			}

			/* Examine the "type" member */
			if (tet.type == 1)
			    formatter.format(" ligature_start");

			else if (tet.type == 10)
			    formatter.format(" ligature_cont");

			/* Separators are only inserted for granularity > word*/
			else if (tet.type == 12)
			    formatter.format(" inserted");

			/* Examine the bit flags in the "attributes" member */
			final int ATTR_NONE = 0;
			final int ATTR_SUB = 1;
			final int ATTR_SUP = 2;
			final int ATTR_DROPCAP = 4;
			final int ATTR_SHADOW = 8;
			final int ATTR_DH_PRE = 16;
			final int ATTR_DH_ARTIFACT = 32;
			final int ATTR_DH_POST = 64;

			if (tet.attributes != ATTR_NONE)
			{
			    if ((tet.attributes & ATTR_SUB) == ATTR_SUB)
				formatter.format("/sub");
			    if ((tet.attributes & ATTR_SUP) == ATTR_SUP)
				formatter.format("/sup");
			    if ((tet.attributes & ATTR_DROPCAP) == ATTR_DROPCAP)
				formatter.format("/dropcap");
			    if ((tet.attributes & ATTR_SHADOW) == ATTR_SHADOW)
				formatter.format("/shadow");
			    if ((tet.attributes & ATTR_DH_PRE) == ATTR_DH_PRE)
				formatter.format("/dehyphenation_pre");
			    if ((tet.attributes & ATTR_DH_ARTIFACT) == ATTR_DH_ARTIFACT)
				formatter.format("/dehyphenation_artifact");
			    if ((tet.attributes & ATTR_DH_POST) == ATTR_DH_POST)
				formatter.format("/dehyphenation_post");
			}

			formatter.format("\n");
		    }

		    formatter.format("\n");
		}
                if (tet.get_errnum() != 0)
                {
                    print_tet_error(tet, pageno);
                }

                tet.close_page(page);
            }

            tet.close_document(doc);
            outfp.close();
        }
	catch (TETException e)
	{
	    System.err.println("TET exception occurred in glyphinfo sample:");
	    System.err.println("[" + e.get_errnum() + "] " + e.get_apiname() +
			    ": " + e.get_errmsg());
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
        }
        finally
        {
            if (tet != null) {
		tet.delete();
            }
        }
    }

    /**
     * Report a TET error.
     * 
     * @param tet The TET object
     * @param pageno The page number on which the error occurred
     */
    private static void print_tet_error(TET tet, int pageno)
    {
        System.err.println("Error " + tet.get_errnum() + " in  "
                + tet.get_apiname() + "() on page " + pageno + ": "
                + tet.get_errmsg());
    }
}
