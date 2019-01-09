package org.cytoscape.io.internal.cx_reader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cytoscape.io.BasicCyFileFilter;
import org.cytoscape.io.DataCategory;
import org.cytoscape.io.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CytoscapeCxFileFilter extends BasicCyFileFilter {
	
	private static final String[] extensions = new String[] { "cx" };
    private static final String[] types = new String[] { "application/json" };
    private static final String description = "CX JSON";
    private static final DataCategory category = DataCategory.NETWORK;
    

    private static final Logger logger            = LoggerFactory.getLogger(CytoscapeCxFileFilter.class);
    public static final Pattern CX_HEADER_PATTERN = Pattern
    													.compile("\\s*\\{\\s*\"\\s*metaData\"\\s*:");
    public static final Pattern CX_HEADER_PATTERN_OLD = Pattern
                                                          .compile("\\s*\\[\\s*\\{\\s*\"\\s*numberVerification\"\\s*:");

    public CytoscapeCxFileFilter(final String[] extensions,
            final String[] contentTypes,
            final String description,
            final DataCategory category,
            final StreamUtil streamUtil) {
		super(extensions, contentTypes, description, category, streamUtil);
	}
    
    public CytoscapeCxFileFilter(final StreamUtil streamUtil) {
        this(extensions, types, description, category, streamUtil);
    }

    @Override
    public boolean accepts(final InputStream stream,
                           final DataCategory category) {
        if (!category.equals(DataCategory.NETWORK)) {
            return false;
        }
        try {
            return (getCXstartElement(stream) != null);
        }
        catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(getClass());
            logger.error("Error while checking header",
                         e);
            return false;
        }
    }

    @Override
    public boolean accepts(final URI uri,
                           final DataCategory category) {
        try (InputStream is = uri.toURL().openStream()) {
			return accepts(is, category);
        }
        catch (final IOException e) {
            logger.error("Error while opening stream: " + uri,
                         e);
            return false;
        }
    }

    /**
     * @param stream
     * @return null if not an CX file
     */
    protected String getCXstartElement(final InputStream stream) {
        final String header = this.getHeader(stream,
                                             20);
        final Matcher matcher = CX_HEADER_PATTERN.matcher(header);
        String root = null;

        if (matcher.find()) {
            root = matcher.group(0);
        }
        if (root == null) {
        	final Matcher matcher_old = CX_HEADER_PATTERN_OLD.matcher(header);
            
            if (matcher_old.find()) {
                root = matcher_old.group(0);
            }
        }

        return root;
    }
    
}
